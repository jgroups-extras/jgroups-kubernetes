package org.jgroups.ping.common.server;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Servers {
    private static final Logger log = Logger.getLogger(Servers.class.getName());

    private static final List<ServerFactory> factories;
    static {
        factories = new ArrayList<ServerFactory>();
        factories.add(new UndertowServerFactory());
        factories.add(new JDKServerFactory());
    }

    /**
     * Create server.
     *
     * @param port the port
     * @param channel the channel
     * @return server instance
     */
    public static Server getServer(int port) {
        for (ServerFactory factory : factories) {
            if (factory.isAvailable()) {
                if (log.isLoggable(Level.FINE)) {
                    log.fine(factory.getClass().getSimpleName() + " is available.");
                }
                return factory.getServer(port);
            } else {
                if (log.isLoggable(Level.FINE)) {
                    log.fine(factory.getClass().getSimpleName() + " is not available.");
                }
            }
        }
        throw new IllegalStateException("No available ServerFactory.");
    }

    private Servers() {}
}
