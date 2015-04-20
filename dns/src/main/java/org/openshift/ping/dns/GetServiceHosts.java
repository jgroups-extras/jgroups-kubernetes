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
        Set<String> serviceHosts = null;
        InetAddress[] inetAddresses = InetAddress.getAllByName(serviceName);
        for (InetAddress inetAddress : inetAddresses) {
            if (serviceHosts == null) {
                serviceHosts = new LinkedHashSet<String>();
            }
            serviceHosts.add(inetAddress.getHostAddress());
        }
        return serviceHosts;
    }

}
