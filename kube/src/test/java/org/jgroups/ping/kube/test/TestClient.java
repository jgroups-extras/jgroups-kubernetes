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

package org.jgroups.ping.kube.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import org.jboss.dmr.ModelNode;
import org.jgroups.ping.kube.Client;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TestClient extends Client {

    private final String file;

    public TestClient() throws URISyntaxException, IOException {
        this(8888);
    }

    public TestClient(int port) throws URISyntaxException, IOException {
        this("pods.json", port);
    }

    public TestClient(String jsonFile, int port) throws URISyntaxException, IOException {
        super(null, null, 0, 0, 0, 0, null, port);
        file = jsonFile;
    }

    @Override
    protected ModelNode getNode(String op, String namespace, String labels) throws Exception {
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(file)) {
            return ModelNode.fromJSONStream(stream);
        }
    }
}
