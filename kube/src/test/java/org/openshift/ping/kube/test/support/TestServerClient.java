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

package org.openshift.ping.kube.test.support;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jboss.dmr.ModelNode;
import org.openshift.ping.kube.Client;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TestServerClient extends Client {
    private static final Map<String, String> ops = new HashMap<>();

    private final static String PODS = "{\n" +
        "    \"kind\": \"PodList\",\n" +
        "    \"apiVersion\": \"v1beta1\",\n" +
        "    \"items\": [\n" +
        "        {\n" +
        "            \"id\": \"my-pod-1\",\n" +
        "            \"labels\": {\n" +
        "                \"name\": \"testRun\",\n" +
        "                \"replicationController\": \"testRun\"\n" +
        "            },\n" +
        "            \"desiredState\": {\n" +
        "              \"manifest\": {\n" +
        "                \"version\": \"v1beta1\",\n" +
        "                \"id\": \"my-pod-1\",\n" +
        "                \"containers\": [{\n" +
        "                  \"name\": \"wildfly\",\n" +
        "                  \"image\": \"redhat/wildfly\",\n" +
        "                  \"ports\": [{\n" +
        "                    \"hostPort\": 8080,\n" +
        "                    \"containerPort\": 80\n" +
        "                    }," +
        "                    {\n" +
        "                      \"hostPort\": 9888,\n" +
        "                      \"name\": \"ping\",\n" +
        "                      \"containerPort\": 8888\n" +
        "                    }]\n" +
        "                }]\n" +
        "              }\n" +
        "            },\n" +
        "            \"currentState\": {\n" +
        "                \"host\": \"localhost\",\n" +
        "                \"podIP\": \"localhost\"\n" +
        "            }\n" +
        "        }\n" +
        "    ]\n" +
        "}";

    static {
        ops.put("pods", PODS);
    }

    @Override
    protected ModelNode getNode(String op, String namespace, String labels) throws IOException {
        String value = ops.get(op);
        if (value == null) {
            throw new IllegalStateException("No such op: " + op);
        }
        return ModelNode.fromJSONString(value);
    }
}
