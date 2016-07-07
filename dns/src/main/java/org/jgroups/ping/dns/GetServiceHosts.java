package org.jgroups.ping.dns;

import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class GetServiceHosts implements Callable<Set<String>> {

    private final String _serviceName;

    public GetServiceHosts(String serviceName) {
        _serviceName = serviceName;
    }

    @Override
    public Set<String> call() throws Exception {
        Set<String> serviceHosts = null;
        InetAddress[] inetAddresses = InetAddress.getAllByName(_serviceName);
        for (InetAddress inetAddress : inetAddresses) {
            if (serviceHosts == null) {
                serviceHosts = new LinkedHashSet<String>();
            }
            serviceHosts.add(inetAddress.getHostAddress());
        }
        return serviceHosts;
    }

}
