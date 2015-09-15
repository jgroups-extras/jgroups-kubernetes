/**
 *  Copyright 2015 Red Hat, Inc.
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
package org.openshift.activemq.discoveryagent;

/**
 * PeerAddressResolver
 * <p/>
 * Interface which provides a list of IP addresses corresponding to peers in a
 * mesh.
 */
public interface PeerAddressResolver {

    /**
     * @return the name of the service providing an openwire (tcp) transport
     */
    public String getServiceName();

    /**
     * @return a list of IPs corresponding to the available peers in a mesh.
     */
    public String[] getPeerIPs();
    
    /**
     * @return the port for the transport associated with the mesh
     */
    public int getServicePort();
}
