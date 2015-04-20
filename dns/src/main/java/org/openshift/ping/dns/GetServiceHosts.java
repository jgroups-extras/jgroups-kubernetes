package org.openshift.ping.dns;

import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.Set;

public class GetServiceHosts implements DnsOperation<Set<String>> {

    private final String serviceName;

    public GetServiceHosts(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public Set<String> call() throws Exception {
        Set<String> serviceHosts = new LinkedHashSet<String>();
        InetAddress[] inetAddresses = InetAddress.getAllByName(serviceName);
        for (InetAddress inetAddress : inetAddresses) {
            serviceHosts.add(inetAddress.getHostAddress());
        }
        return serviceHosts;
    }

}
