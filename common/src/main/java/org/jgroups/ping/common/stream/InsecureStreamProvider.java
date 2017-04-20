package org.jgroups.ping.common.stream;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InsecureStreamProvider extends BaseStreamProvider {
    private static final Logger log = Logger.getLogger(InsecureStreamProvider.class.getName());

    static final HostnameVerifier INSECURE_HOSTNAME_VERIFIER =(arg0, arg1) -> true;

    static final TrustManager[] INSECURE_TRUST_MANAGERS ={
        new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }
    };

    private final SSLSocketFactory factory;

    public InsecureStreamProvider() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null,  INSECURE_TRUST_MANAGERS, null);
        factory = context.getSocketFactory();
    }

    @Override
    public InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException {
        URLConnection connection = openConnection(url, headers, connectTimeout, readTimeout);
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = HttpsURLConnection.class.cast(connection);
            httpsConnection.setHostnameVerifier(INSECURE_HOSTNAME_VERIFIER);
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

}
