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

package org.openshift.ping.common.server;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.PhysicalAddress;
import org.jgroups.View;
import org.jgroups.protocols.PingData;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractServer implements Server {

    protected final int port;
    protected final Map<String, Channel> CHANNELS = new HashMap<String, Channel>();

    protected AbstractServer(int port) {
        this.port = port;
    }

    public final Channel getChannel(String clusterName) {
        if (clusterName != null) {
            synchronized (CHANNELS) {
                return CHANNELS.get(clusterName);
            }
        }
        return null;
    }

    protected final void addChannel(Channel channel) {
        String clusterName = getClusterName(channel);
        if (clusterName != null) {
            synchronized (CHANNELS) {
                CHANNELS.put(clusterName, channel);
            }
        }
    }

    protected final void removeChannel(Channel channel) {
        String clusterName = getClusterName(channel);
        if (clusterName != null) {
            synchronized (CHANNELS) {
                CHANNELS.remove(clusterName);
            }
        }
    }

    protected final boolean hasChannels() {
        synchronized (CHANNELS) {
            return CHANNELS.isEmpty();
        }
    }

    private String getClusterName(final Channel channel) {
        if (channel != null) {
            String clusterName = channel.getClusterName();
            // clusterName will be null if the Channel is not yet connected, but we still need it!
            if (clusterName == null && channel instanceof JChannel) {
                try {
                    Field field = JChannel.class.getDeclaredField("cluster_name");
                    field.setAccessible(true);
                    return (String)field.get(channel);
                } catch (Throwable t) {}
            }
        }
        return null;
    }

    /**
     * Create ping data from channel.
     *
     * @param channel the channel
     * @return ping data
     */
    public static final PingData createPingData(Channel channel) {
        Address address = channel.getAddress();
        View view = channel.getView();
        boolean is_server = false;
        String logical_name = channel.getName();
        PhysicalAddress paddr = (PhysicalAddress)channel.down(new Event(Event.GET_PHYSICAL_ADDRESS, address));
        return new PingData(address, view, is_server, logical_name, Collections.singleton(paddr));
    }
}
