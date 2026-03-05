/*
 *  Copyright 2019 Red Hat, Inc.
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

package org.jgroups.protocols.kubernetes.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashMap;
import java.util.Map;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Verify {@link TokenStreamProvider} and {@link CertificateStreamProvider} correctly parse all certificates from the file.
 *
 * @author Radoslav Husar
 */
public class StreamProviderTest {

    private static final String CA_FILE = StreamProviderTest.class.getResource("/certificates/ca.crt").getFile();

    @Test
    public void testTokenStreamProviderCaCert() throws Exception {
        testConfigureCaCert(TokenStreamProvider.configureCaCert(CA_FILE));
    }

    @Test
    public void testCertificateStreamProviderCaCert() throws Exception {
        testConfigureCaCert(CertificateStreamProvider.configureCaCert(CA_FILE));
    }

    @Test
    public void testTokenStreamProviderDoesNotMutateSharedHeaders() throws Exception {
        Path tokenFile = Files.createTempFile("sa-token", ".txt");
        Files.write(tokenFile, "token-value".getBytes(StandardCharsets.UTF_8));
        try {
            final AtomicReference<Map<String, String>> capturedHeaders = new AtomicReference<>();
            TokenStreamProvider provider = new TokenStreamProvider(tokenFile.toString(), null) {
                @Override
                public URLConnection openConnection(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException {
                    capturedHeaders.set(new HashMap<>(headers));
                    return new URLConnection(new URL(url)) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(new byte[0]);
                        }
                    };
                }
            };

            Map<String, String> sharedHeaders = new HashMap<>();
            sharedHeaders.put("Accept", "application/json");
            provider.openStream("http://localhost", sharedHeaders, 0, 0).close();

            assertTrue(sharedHeaders.containsKey("Accept"));
            assertFalse(sharedHeaders.containsKey(TokenStreamProvider.AUTHORIZATION));
            assertEquals("Bearer token-value", capturedHeaders.get().get(TokenStreamProvider.AUTHORIZATION));
        } finally {
            Files.deleteIfExists(tokenFile);
        }
    }

    private static void testConfigureCaCert(TrustManager[] trustManagers) {
        assertEquals(1, trustManagers.length);
        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
        X509Certificate[] acceptedIssuers = trustManager.getAcceptedIssuers();

        // Assert all 4 CA certs are parsed
        assertTrue(Stream.of("CN=kube-apiserver-localhost-signer", "CN=kube-apiserver-service-network-signer", "CN=ingress-operator@1559194394", "CN=kube-apiserver-lb-signer")
                .allMatch(cn -> Stream.of(acceptedIssuers)
                        .anyMatch(c -> c.getSubjectDN().toString().startsWith(cn)))
        );
    }
}
