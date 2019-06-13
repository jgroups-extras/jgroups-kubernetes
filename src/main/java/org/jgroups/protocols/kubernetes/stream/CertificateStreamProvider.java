package org.jgroups.protocols.kubernetes.stream;

import static org.jgroups.protocols.kubernetes.Utils.openFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Collection;
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

import org.jgroups.protocols.kubernetes.pem.PEMReader;
import org.jgroups.protocols.kubernetes.pem.PKCS1EncodedKeySpec;


/**
 * @author From Fabric8
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CertificateStreamProvider extends BaseStreamProvider {
    private static final Logger log = Logger.getLogger(CertificateStreamProvider.class.getName());

    private final SSLSocketFactory factory;

    public CertificateStreamProvider(String clientCertFile, String clientKeyFile, String clientKeyPassword, String clientKeyAlgo, String caCertFile) throws Exception {
        // defaults - RSA and empty password
        char[] password = (clientKeyPassword != null) ? clientKeyPassword.toCharArray() : new char[0];
        String algorithm = (clientKeyAlgo != null) ? clientKeyAlgo : "RSA";

        KeyManager[] keyManagers = configureClientCert(clientCertFile, clientKeyFile, password, algorithm);
        TrustManager[] trustManagers = configureCaCert(caCertFile);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, null);
        factory = context.getSocketFactory();
    }

    public InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException {
        URLConnection connection = openConnection(url, headers, connectTimeout, readTimeout);
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = HttpsURLConnection.class.cast(connection);
            //httpsConnection.setHostnameVerifier(InsecureStreamProvider.INSECURE_HOSTNAME_VERIFIER);
            httpsConnection.setSSLSocketFactory(factory);
            if (log.isLoggable(Level.FINE)) {
                log.fine(String.format("Using HttpsURLConnection with SSLSocketFactory [%s] for url [%s].", factory, url));
            }
        } else {
            if (log.isLoggable(Level.FINE)) {
                log.fine(String.format("Using URLConnection for url [%s].", url));
            }
        }
        return connection.getInputStream();
    }

    private static KeyManager[] configureClientCert(String clientCertFile, String clientKeyFile, char[] clientKeyPassword, String clientKeyAlgo) throws Exception {
        try {
            InputStream certInputStream = openFile(clientCertFile);
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate)certFactory.generateCertificate(certInputStream);

            InputStream keyInputStream = openFile(clientKeyFile);
            PEMReader reader = new PEMReader(keyInputStream);
            RSAPrivateCrtKeySpec keySpec = new PKCS1EncodedKeySpec(reader.getDerBytes()).getKeySpec();
            KeyFactory kf = KeyFactory.getInstance(clientKeyAlgo);
            RSAPrivateKey privKey = (RSAPrivateKey)kf.generatePrivate(keySpec);

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

    static TrustManager[] configureCaCert(String caCertFile) throws Exception {
        if (caCertFile != null && !caCertFile.isEmpty()) {
            try {
                InputStream pemInputStream = openFile(caCertFile);
                CertificateFactory certFactory = CertificateFactory.getInstance("X509");

                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(null);

                Collection<? extends Certificate> certificates = certFactory.generateCertificates(pemInputStream);
                for (Certificate c : certificates) {
                    X509Certificate certificate = (X509Certificate) c;
                    String alias = certificate.getSubjectX500Principal().getName();
                    trustStore.setCertificateEntry(alias, certificate);
                }

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);

                return trustManagerFactory.getTrustManagers();
            } catch (FileNotFoundException ignore) {
                log.log(Level.WARNING, "ca cert file not found " + caCertFile + " - defaulting to insecure trust manager");
                return TrustManagers.INSECURE_TRUST_MANAGERS;
            } catch (Exception e) {
                log.log(Level.SEVERE, "Could not create trust manager for " + caCertFile, e);
                throw e;
            }
        } else {
            if (log.isLoggable(Level.WARNING)) {
                log.log(Level.WARNING, "ca cert file undefined - defaulting to insecure trust manager");
            }
            return TrustManagers.INSECURE_TRUST_MANAGERS;
        }
    }

}
