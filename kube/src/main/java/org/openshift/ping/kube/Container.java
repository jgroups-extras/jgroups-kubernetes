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
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public final class Container {
    private final List<Port> ports = new ArrayList<Port>();

    public Container() {}

    void addPort(Port port) {
        ports.add(port);
    }

    public List<Port> getPorts() {
        return Collections.unmodifiableList(ports);
    }

    public Port getPort(String name) {
        for (Port port : ports) {
            if (name.equals(port.getName())) {
                return port;
            }
        }
        //return null;
        throw new IllegalArgumentException("No such port: " + name);
    }

    public String toString() {
        return String.format("%s[ports=%s]", getClass().getSimpleName(), ports);
    }
}
