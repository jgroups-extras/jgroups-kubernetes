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

package org.openshift.ping.server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import net.oauth.signature.pem.PEMReader;
import net.oauth.signature.pem.PKCS1EncodedKeySpec;

/**
 * @author From Fabric8
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Certs {
    private static final Logger log = Logger.getLogger(Certs.class.getName());

    private final SSLSocketFactory factory;

    public Certs(String clientCertFile, String clientKeyFile, String clientKeyPassword, String clientKeyAlgo, String caCertFile) throws Exception {
        // defaults - RSA and empty password
        char[] password = (clientKeyPassword != null) ? clientKeyPassword.toCharArray() : new char[0];
        String algorithm = (clientKeyAlgo != null) ? clientKeyAlgo : "RSA";

        KeyManager[] keyManagers = configureClientCert(clientCertFile, clientKeyFile, password, algorithm);
        TrustManager[] trustManagers = configureCaCert(caCertFile);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, null);
        factory = context.getSocketFactory();
    }

    public InputStream openStream(String url) throws Exception {
        return openStream(url, null);
    }

    public InputStream openStream(String url, Map<String, String> headers) throws Exception {
        URL requestedUrl = new URL(url);
        URLConnection connection = requestedUrl.openConnection();
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection.class.cast(connection).setSSLSocketFactory(factory);
            if (log.isLoggable(Level.FINE)) {
                log.fine(String.format("Using HttpsURLConnection with SSLSocketFactory [%s] for url [%s].", factory, url));
            }
        } else {
            if (log.isLoggable(Level.FINE)) {
                log.fine(String.format("Using URLConnection for url [%s].", url));
            }
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return connection.getInputStream();
    }

    private InputStream getInputStreamFromFile(String file) throws FileNotFoundException {
        if (file != null) {
            return new FileInputStream(file);
        }
        return null;
    }

    private KeyManager[] configureClientCert(String clientCertFile, String clientKeyFile, char[] clientKeyPassword, String clientKeyAlgo) throws Exception {
        try {
            InputStream certInputStream = getInputStreamFromFile(clientCertFile);
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certInputStream);

            InputStream keyInputStream = getInputStreamFromFile(clientKeyFile);
            PEMReader reader = new PEMReader(keyInputStream);
            RSAPrivateCrtKeySpec keySpec = new PKCS1EncodedKeySpec(reader.getDerBytes()).getKeySpec();
            KeyFactory kf = KeyFactory.getInstance(clientKeyAlgo);
            RSAPrivateKey privKey = (RSAPrivateKey) kf.generatePrivate(keySpec);

            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null);

            String alias = cert.getSubjectX500Principal().getName();
            keyStore.setKeyEntry(alias, privKey, clientKeyPassword, new Certificate[]{cert});

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, clientKeyPassword);

            return keyManagerFactory.getKeyManagers();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not create key manager for " + clientCertFile + " (" + clientKeyFile + ")", e);
            throw e;
        }
    }

    private TrustManager[] configureCaCert(String caCertFile) throws Exception {
        try {
            InputStream pemInputStream = getInputStreamFromFile(caCertFile);
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(pemInputStream);

            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(null);

            String alias = cert.getSubjectX500Principal().getName();
            trustStore.setCertificateEntry(alias, cert);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            return trustManagerFactory.getTrustManagers();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not create trust manager for " + caCertFile, e);
            throw e;
        }
    }

}
