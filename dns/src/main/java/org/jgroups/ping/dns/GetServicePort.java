package org.jgroups.ping.dns;

import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class GetServicePort implements Callable<Integer> {

    private final String _serviceName;

    public GetServicePort(String serviceName) {
        _serviceName = serviceName;
    }

    @Override
    public Integer call() throws Exception {
        Set<DnsRecord> dnsRecords = getDnsRecords(_serviceName);
        for (DnsRecord dnsRecord : dnsRecords) {
            /*
            if (serviceName.equals(dnsRecord.getHost())) {
                return dnsRecord.getPort();
            }
            */
            // they should all match, even if individual names are different
            return dnsRecord.getPort();
        }
        return null;
    }

    private Set<DnsRecord> getDnsRecords(String serviceName) throws Exception {
        Set<DnsRecord> dnsRecords = new TreeSet<DnsRecord>();
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put(Context.PROVIDER_URL, "dns:");
        env.put("com.sun.jndi.dns.recursion", "false");
        // default is one second, but os skydns can be slow
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        // retries handled by DnsPing
        //env.put("com.sun.jndi.dns.timeout.retries", "4");
        DirContext ctx = new InitialDirContext(env);
        Attributes attrs = ctx.getAttributes(serviceName, new String[]{"SRV"});
        NamingEnumeration<?> servers = attrs.get("SRV").getAll();
        while (servers.hasMore()) {
            DnsRecord record = DnsRecord.fromString((String)servers.next());
            dnsRecords.add(record);
        }
        return dnsRecords;
    }

}
