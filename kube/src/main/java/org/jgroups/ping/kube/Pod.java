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

package org.jgroups.ping.kube;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public final class Pod {
    private final String podIP;
    private final List<Container> containers = new ArrayList<Container>();

    public Pod(String podIP) {
        this.podIP = podIP;
    }

    public String getPodIP() {
        return podIP;
    }

    void addContainer(Container container) {
        containers.add(container);
    }

    public List<Container> getContainers() {
        return Collections.unmodifiableList(containers);
    }

    public String toString() {
        return String.format("%s[podIP=%s, containers=%s]", getClass().getSimpleName(), podIP, containers);
    }
}
