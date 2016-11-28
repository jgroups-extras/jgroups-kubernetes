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

package org.jgroups.ping.common.server;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.jgroups.JChannel;
import org.jgroups.ping.common.OpenshiftPing;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractServer implements Server {

    protected final int port;
    protected final Map<String, JChannel> CHANNELS = new HashMap<>();

    protected AbstractServer(int port) {
        this.port = port;
    }

    public final JChannel getChannel(String clusterName) {
        if (clusterName != null) {
            synchronized (CHANNELS) {
                return CHANNELS.get(clusterName);
            }
        }
        return null;
    }

    protected final void addChannel(JChannel channel) {
        String clusterName = getClusterName(channel);
        if (clusterName != null) {
            synchronized (CHANNELS) {
                CHANNELS.put(clusterName, channel);
            }
        }
    }

    protected final void removeChannel(JChannel channel) {
        String clusterName = getClusterName(channel);
        if (clusterName != null) {
            synchronized (CHANNELS) {
                CHANNELS.remove(clusterName);
            }
        }
    }

    protected final boolean hasChannels() {
        synchronized (CHANNELS) {
            return !CHANNELS.isEmpty();
        }
    }

    private String getClusterName(final JChannel channel) {
        if (channel != null) {
            String clusterName = channel.getClusterName();
            // clusterName will be null if the Channel is not yet connected, but we still need it!
            if (clusterName == null) {
                try {
                    Field field = JChannel.class.getDeclaredField("cluster_name");
                    field.setAccessible(true);
                    return (String)field.get(channel);
                } catch (Throwable t) {}
            }
        }
        return null;
    }

    protected final void handlePingRequest(JChannel channel, InputStream stream) throws Exception {
    	if (channel != null) {
    		OpenshiftPing handler = (OpenshiftPing) channel.getProtocolStack().findProtocol(OpenshiftPing.class);
            handler.handlePingRequest(stream);
    	}
    }
}
