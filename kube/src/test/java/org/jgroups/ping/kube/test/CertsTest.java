/**
 * Copyright 2014 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.jgroups.ping.kube.test;

import static org.jgroups.ping.common.Utils.getSystemEnv;
import static org.jgroups.ping.common.Utils.getSystemProperty;

import java.io.InputStream;

import org.junit.Test;
import org.jgroups.ping.common.stream.CertificateStreamProvider;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CertsTest {

    private static String getValue(String name) {
        return getValue(name, null);
    }

    private static String getValue(String name, String defaultValue) {
        String value = getSystemEnv(name);
        return value != null ? value : getSystemProperty(name, defaultValue);
    }

    @Test
    public void testCerts() throws Exception {
        String clientCertFile = getValue("KUBERNETES_CLIENT_CERTIFICATE_FILE");
        String clientKeyFile = getValue("KUBERNETES_CLIENT_KEY_FILE");
        String clientKeyPassword = getValue("KUBERNETES_CLIENT_KEY_PASSWORD");
        String clientKeyAlgo = getValue("KUBERNETES_CLIENT_KEY_ALGO");
        String caCertFile = getValue("KUBERNETES_CA_CERTIFICATE_FILE");

        if (clientCertFile == null) {
            return;
        }

        CertificateStreamProvider certStreamProvider =
                new CertificateStreamProvider(clientCertFile, clientKeyFile, clientKeyPassword, clientKeyAlgo, caCertFile);

        String k8s_master = getValue("KUBERNETES_MASTER");
        String apiVersion = getValue("API_VERSION", "v1beta1");
        String op = getValue("OP", "pods");

        try (InputStream is = certStreamProvider.openStream(String.format("%s/api/%s/%s", k8s_master, apiVersion, op), null, 0, 0)) {
            int x;
            while ((x = is.read()) != -1) {
                System.out.print((char) x);
            }
        }
    }

}
