package org.jgroups.ping.common.server;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractServerFactory implements ServerFactory {

    private static final Map<Integer, Server> SERVERS = new HashMap<Integer, Server>();

    @Override
    public synchronized final Server getServer(int port) {
        Server server = SERVERS.get(port);
        if (server == null) {
            server = createServer(port);
            SERVERS.put(port, server);
        }
        return server;
    }

    public abstract Server createServer(int port);

}
