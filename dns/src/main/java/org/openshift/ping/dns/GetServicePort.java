package org.openshift.ping.dns;

import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class GetServicePort implements DnsOperation<Integer> {

    private final String serviceName;

    public GetServicePort(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public Integer call() throws Exception {
        Set<DnsRecord> dnsRecords = getDnsRecords(serviceName);
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
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
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
