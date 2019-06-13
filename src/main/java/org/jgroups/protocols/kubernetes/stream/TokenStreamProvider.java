package org.jgroups.protocols.kubernetes.stream;

import static org.jgroups.protocols.kubernetes.Utils.openFile;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copied and adapted version from openshift-ping repository. A warning message is issued if ca cert file is undefined or not
 * found rather than always using insecure stream or being mandatory and eventually failing.
 *
 * @author <a href="mailto:rmartine@redhat.com">Ricardo Martinelli</a>
 * @author Radoslav Husar
 */
public class TokenStreamProvider extends BaseStreamProvider {

    private static final Logger log = Logger.getLogger(TokenStreamProvider.class.getName());

    private String token;

    private String caCertFile;

    private SSLSocketFactory factory;

    public TokenStreamProvider(String token, String caCertFile) {
        this.token = token;
        this.caCertFile = caCertFile;
    }

    @Override
    public InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout)
            throws IOException {
        URLConnection connection = openConnection(url, headers, connectTimeout, readTimeout);

        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = HttpsURLConnection.class.cast(connection);
            //httpsConnection.setHostnameVerifier(InsecureStreamProvider.INSECURE_HOSTNAME_VERIFIER);
            httpsConnection.setSSLSocketFactory(getSSLSocketFactory());
            if (log.isLoggable(Level.FINE)) {
                log.fine(String.format("Using HttpsURLConnection with SSLSocketFactory [%s] for url [%s].", factory, url));
            }
        } else {
            if (log.isLoggable(Level.FINE)) {
                log.fine(String.format("Using URLConnection for url [%s].", url));
            }
        }

        if (token != null) {
            // curl -k -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
            // https://172.30.0.2:443/api/v1/namespaces/dward/pods?labelSelector=application%3Deap-app
            headers.put("Authorization", "Bearer " + token);
        }
        return connection.getInputStream();
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
            log.log(Level.WARNING, "ca cert file undefined - defaulting to insecure trust manager");
            return TrustManagers.INSECURE_TRUST_MANAGERS;
        }
    }

    private SSLSocketFactory getSSLSocketFactory() throws IOException {
        if(this.factory == null) {
            synchronized(this) {
                if(this.factory == null) {
                    try {
                        TrustManager[] trustManagers = configureCaCert(this.caCertFile);
                        SSLContext context = SSLContext.getInstance("TLS");
                        context.init(null, trustManagers, null);
                        this.factory = context.getSocketFactory();
                    } catch(Exception e) {
                        throw new IOException(e);
                    }
                }
            }
        }
        return this.factory;
    }

}
