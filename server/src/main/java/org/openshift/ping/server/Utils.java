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

package org.openshift.ping.server;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.Event;
import org.jgroups.PhysicalAddress;
import org.jgroups.View;
import org.jgroups.protocols.PingData;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Utils {
    private static final Logger log = Logger.getLogger(Utils.class.getName());

    private static final List<ServerFactory> factories;
    static {
        factories = new ArrayList<ServerFactory>();
        factories.add(new UndertowServerFactory());
        factories.add(new JBossServerFactory());
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

    /**
     * Create ping data from channel.
     *
     * @param channel the channel
     * @return ping data
     */
    public static PingData createPingData(Channel channel) {
        Address address = channel.getAddress();
        View view = channel.getView();
        boolean is_server = false;
        String logical_name = channel.getName();
        PhysicalAddress paddr = (PhysicalAddress)channel.down(new Event(Event.GET_PHYSICAL_ADDRESS, address));
        return new PingData(address, view, is_server, logical_name, Collections.singleton(paddr));
    }

    public static InputStream openStream(String url, Map<String, String> headers, int tries, long sleep) {
        return openStream(url, headers, tries, sleep, null);
    }

    public static InputStream openStream(String url, Map<String, String> headers, int tries, long sleep, Certs certs) {
        final int attempts = tries;
        Throwable lastFail = null;
        while (tries > 0) {
            tries--;
            try {
                if (certs != null) {
                    return certs.openStream(url, headers);
                } else {
                    URL xurl = new URL(url);
                    URLConnection connection = xurl.openConnection();
                    if (headers != null) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            connection.addRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                    return connection.getInputStream();
                }
            } catch (Throwable fail) {
                lastFail = fail;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        String emsg = String.format("%s attempt(s) to open stream [%s] failed. Last failure was [%s: %s].",
                attempts, url,
                (lastFail != null ? lastFail.getClass().getName() : "null"),
                (lastFail != null ? lastFail.getMessage() : ""));
        throw (lastFail != null) ? new IllegalStateException(emsg, lastFail) : new IllegalStateException(emsg);
    }

}
