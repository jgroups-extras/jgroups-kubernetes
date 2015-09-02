/**
 *  Copyright 2014 Red Hat, Inc.
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

package org.openshift.ping.dns;

import static org.openshift.ping.common.Utils.execute;
import static org.openshift.ping.common.Utils.getSystemEnv;
import static org.openshift.ping.common.Utils.getSystemEnvInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jgroups.annotations.MBean;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.PingData;
import org.openshift.ping.common.OpenshiftPing;

@MBean(description = "DNS based discovery protocol")
public class DnsPing extends OpenshiftPing {

    public static final short OPENSHIFT_DNS_PING_ID = 2020;
    public static final short JGROUPS_DNS_PING_ID = 2021;
    static {
        ClassConfigurator.addProtocol(OPENSHIFT_DNS_PING_ID, DnsPing.class);
    }

    @Property
    private String serviceName; // DO NOT HARDCODE A DEFAULT (i.e.: "ping") - SEE isClusteringEnabled() and init() METHODS BELOW!
    private String _serviceName;

    @Property
    private int servicePort;
    private int _servicePort;

    public DnsPing() {
        super("OPENSHIFT_DNS_PING_");
    }

    @Override
    protected boolean isClusteringEnabled() {
        return _serviceName != null;
    }

    @Override
    protected int getServerPort() {
        return _servicePort;
    }

    @Override
    public void init() throws Exception {
        super.init();
        _serviceName = getSystemEnv(getSystemEnvName("SERVICE_NAME"), serviceName, true);
        if (_serviceName == null) {
            if (log.isInfoEnabled()) {
                log.info(String.format("serviceName not set; clustering disabled"));
            }
            // no further initialization necessary
            return;
        }
        if (log.isInfoEnabled()) {
            log.info(String.format("serviceName [%s] set; clustering enabled", _serviceName));
        }
        _servicePort = getServicePort();
    }

    @Override
    public void destroy() {
        _serviceName = null;
        _servicePort = 0;
        super.destroy();
    }

    private int getServicePort() {
        int svcPort = getSystemEnvInt(getSystemEnvName("SERVICE_PORT"));
        if (svcPort < 1) {
            svcPort = servicePort;
            if (svcPort < 1) {
                Integer dnsPort = execute(new GetServicePort(_serviceName), getOperationAttempts(), getOperationSleep());
                if (dnsPort != null) {
                    svcPort = dnsPort.intValue();
                } else if (log.isWarnEnabled()) {
                    log.warn(String.format("No DNS SRV record found for service [%s]", _serviceName));
                }
                if (svcPort < 1) {
                    svcPort = 8888;
                }
            }
        }
        return svcPort;
    }

    private Set<String> getServiceHosts() {
        Set<String> svcHosts = execute(new GetServiceHosts(_serviceName), getOperationAttempts(), getOperationSleep());
        if (svcHosts == null) {
            svcHosts = Collections.emptySet();
            if (log.isWarnEnabled()) {
                log.warn(String.format("No matching hosts found for service [%s]; continuing...", _serviceName));
            }
        }
        return svcHosts;
    }

    @Override
    protected synchronized List<PingData> doReadAll(String clusterName) {
        Set<String> serviceHosts = getServiceHosts();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Reading service hosts %s on port [%s]", serviceHosts, _servicePort));
        }
        List<PingData> retval = new ArrayList<>();
        boolean localAddrPresent = false;
        for (String serviceHost : serviceHosts) {
            try {
                PingData pingData = getPingData(serviceHost, _servicePort, clusterName);
                localAddrPresent = localAddrPresent || pingData.getAddress().equals(local_addr);
                retval.add(pingData);
            } catch (Exception e) {
                if (log.isInfoEnabled()) {
                    log.info(String.format("PingData not available for cluster [%s], service [%s], host [%s], port [%s]; encountered [%s: %s]",
                            clusterName, _serviceName, serviceHost, _servicePort, e.getClass().getName(), e.getMessage()));
                }
            }
        }
        if (localAddrPresent) {
            if (log.isDebugEnabled()) {
                for (PingData pingData: retval) {
                    log.debug(String.format("Returning PingData [%s]", pingData));
                }
            }
            return retval;
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Local address not discovered, returning empty list");
            }
            return Collections.<PingData>emptyList();
        }
    }

}
