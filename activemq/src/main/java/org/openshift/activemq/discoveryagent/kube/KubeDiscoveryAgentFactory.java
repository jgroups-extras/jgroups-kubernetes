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
package org.openshift.activemq.discoveryagent.kube;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.apache.activemq.transport.discovery.DiscoveryAgent;
import org.apache.activemq.transport.discovery.DiscoveryAgentFactory;
import org.apache.activemq.util.IOExceptionSupport;
import org.apache.activemq.util.IntrospectionSupport;
import org.apache.activemq.util.URISupport;
import org.openshift.activemq.discoveryagent.OpenShiftDiscoveryAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KubeDiscoveryAgentFactory
 */
public class KubeDiscoveryAgentFactory extends DiscoveryAgentFactory {

    private final static Logger LOGGER = LoggerFactory.getLogger(KubeDiscoveryAgentFactory.class);

    @Override
    protected DiscoveryAgent doCreateDiscoveryAgent(URI uri) throws IOException {
        try {
            LOGGER.info("Creating Kubernetes discovery agent for {}.", uri.toString());
            final Map<String, String> options = URISupport.parseParameters(uri);
            uri = URISupport.removeQuery(uri);
            final OpenShiftDiscoveryAgent agent = new OpenShiftDiscoveryAgent(new KubePeerAddressResolver(
                    uri.getHost(), uri.getPort()));
            IntrospectionSupport.setProperties(agent, options);
            return agent;
        } catch (Throwable e) {
            throw IOExceptionSupport.create("Could not create discovery agent: " + uri, e);
        }
    }

}
