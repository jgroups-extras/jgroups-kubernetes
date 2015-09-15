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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsecureStreamProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(InsecureStreamProvider.class.getName());

    static final HostnameVerifier INSECURE_HOSTNAME_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String arg0, SSLSession arg1) {
            return true;
        }
    };

    static final TrustManager[] INSECURE_TRUST_MANAGERS = new TrustManager[] {new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    } };

    private final SSLSocketFactory factory;
    private final AtomicBoolean errorLogged = new AtomicBoolean();

    public InsecureStreamProvider() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, INSECURE_TRUST_MANAGERS, null);
        factory = context.getSocketFactory();
    }

    public URLConnection openConnection(String url, Map<String, String> headers, int connectTimeout, int readTimeout)
            throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format(
                    "%s opening connection: url [%s], headers [%s], connectTimeout [%s], readTimeout [%s]", getClass()
                            .getSimpleName(), url, headers, connectTimeout, readTimeout));
        }
        URLConnection connection = new URL(url).openConnection();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        if (connectTimeout < 0 || readTimeout < 0) {
            throw new IllegalArgumentException(String.format(
                    "Neither connectTimeout [%s] nor readTimeout [%s] can be less than 0 for URLConnection.",
                    connectTimeout, readTimeout));
        }
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        return connection;
    }

    public InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout)
            throws IOException {
        URLConnection connection = openConnection(url, headers, connectTimeout, readTimeout);
        try {
            if (connection instanceof HttpsURLConnection) {
                HttpsURLConnection httpsConnection = HttpsURLConnection.class.cast(connection);
                httpsConnection.setHostnameVerifier(INSECURE_HOSTNAME_VERIFIER);
                httpsConnection.setSSLSocketFactory(factory);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Using HttpsURLConnection with SSLSocketFactory [%s] for url [%s].",
                            factory, url));
                }
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Using URLConnection for url [%s].", url));
                }
            }
            return connection.getInputStream();
        } catch (IOException e) {
            if (connection instanceof HttpURLConnection) {
                switch (((HttpURLConnection) connection).getResponseCode()) {
                case 401:
                case 403:
                    if (errorLogged.compareAndSet(false, true)) {
                        LOGGER.error(String.format(
                                "Authentication failed for [%s].  Ensure service account has view privileges.", url));
                    }
                    return new ByteArrayInputStream("{}".getBytes());
                }
            }
            throw e;
        }
    }

}
