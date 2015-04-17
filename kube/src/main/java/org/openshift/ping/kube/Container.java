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

package org.openshift.ping.kube;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Container {
    private String name;
    private String host;
    private String podIP;
    private List<Port> ports = new ArrayList<>();

    public Container(String host, String podIP) {
        this.host = host;
        this.podIP = podIP;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    void addPort(Port port) {
        ports.add(port);
    }

    public String getHost() {
        return host;
    }

    public List<Port> getPorts() {
        return ports;
    }

    public String getPodIP() {
        return podIP;
    }

    public void setPodIP(String podIP) {
        this.podIP = podIP;
    }

    public Port getPort(String name) {
        for (Port port : ports) {
            if (name.equals(port.getName())) {
                return port;
            }
        }
        throw new IllegalArgumentException("No such port: " + name);
    }
}
