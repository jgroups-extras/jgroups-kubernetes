/**
 *  Copyright 2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.openshift.activemq.discoveryagent.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for querying OpenShift DNS server for services and endpoints.
 */
public class DNSUtil {

    private final static Logger LOGGER = LoggerFactory.getLogger(DNSUtil.class);

    private static final String KUBERNETES_SERVICE_NAME = "kubernetes.default.svc";

    private String domain;
    private String serviceDomain;
    private String endpointsDomain;

    /**
     * Create a new DNSUtil using default DNS server (i.e. dns:)
     * 
     * @throws NamingException if something goes awry
     */
    public DNSUtil() {
        initDomain();
    }

    /**
     * Returns a list of IPs corresponding to the endpoints registered for the
     * specified service.
     * 
     * @param serviceName the name of the service. This may be a fully qualified
     *            name or a subdomain name that can be resolved using the search
     *            definition on the host machine.
     * @return a list of IPs
     */
    public String[] getEndpointsForService(String serviceName) {
        final String endpointName = getEndpointNameForService(serviceName);
        return lookupIPs(endpointName);
    }

    /**
     * Returns the endpoint name for the service (i.e. .endpoints.doman vs.
     * .svc.domain).
     * 
     * @param serviceName the service name
     * @return the fully qualified endpoint name
     */
    public String getEndpointNameForService(String serviceName) {
        final String fqdn = getFQDN(serviceName);
        if (fqdn == null || fqdn.length() == 0 || fqdn.length() < serviceDomain.length()) {
            return null;
        }
        return fqdn.substring(0, fqdn.length() - serviceDomain.length()) + endpointsDomain;
    }

    /**
     * Returns the fully qualified domain name for the given name. If the name
     * is already a FQDN, the returned value should match the input.
     * 
     * @param name the name to fully qualify.
     * @return the fully qualified domain name; null if the name could not be
     *         resolved.
     */
    public String getFQDN(String name) {
        if (name == null) {
            return null;
        }
        if (domain != null && name.endsWith(domain)) {
            return name;
        }
        try {
            for (InetAddress inetAddress : InetAddress.getAllByName(name)) {
                return InetAddress.getByAddress(inetAddress.getAddress()).getHostName();
            }
        } catch (UnknownHostException e) {
            LOGGER.warn("Could not resolve host: {}", name, e);
        }
        return null;
    }

    /**
     * Returns a list of IP addresses for the given name.
     * 
     * @param name the name to lookup
     * @return the list of IPs for the name
     */
    public String[] lookupIPs(String name) {
        if (name == null) {
            return new String[0];
        }
        try {
            List<String> retVal = new ArrayList<String>();
            for (InetAddress inetAddress : InetAddress.getAllByName(name)) {
                retVal.add(inetAddress.getHostAddress());
            }
            return retVal.toArray(new String[retVal.size()]);
        } catch (UnknownHostException e) {
            LOGGER.warn("Could not resolve host: {}", name, e);
            return new String[0];
        }
    }

    /**
     * Return the port for the specified service.
     * 
     * @param name the service name
     * @return the port
     */
    public String getPortForService(String name) {
        if (name == null) {
            return null;
        }
        DirContext ctx = null;
        try {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put(Context.PROVIDER_URL, "dns:");
            env.put("com.sun.jndi.dns.recursion", "false");
            // default is one second, but os skydns can be slow
            env.put("com.sun.jndi.dns.timeout.initial", "2000");
            env.put("com.sun.jndi.dns.timeout.retries", "4");
            ctx = new InitialDirContext(env);
            for (NamingEnumeration<?> srvs = ctx.getAttributes(name, new String[] {"SRV" }).getAll(); srvs.hasMore();) {
                String srv = srvs.next().toString();
                String[] fields = srv.split(" ");
                return fields[2];
            }
        } catch (NamingException e) {
            LOGGER.error("Error retrieving port for service: " + name, e);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    e.fillInStackTrace();
                }
            }
        }
        return null;
    }

    private void initDomain() {
        String fqdn = getFQDN(KUBERNETES_SERVICE_NAME);
        if (fqdn != null && fqdn.startsWith(KUBERNETES_SERVICE_NAME)) {
            domain = fqdn.substring(KUBERNETES_SERVICE_NAME.length() + 1);
        } else {
            LOGGER.warn("Could not resolve kubernetes service to calculate default domain.  Using cluster.local as domain");
            domain = "cluster.local";
        }
        serviceDomain = ".svc." + domain;
        endpointsDomain = ".endpoints." + domain;
    }

}
