package org.jgroups.protocols.kubernetes.stream;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseStreamProvider implements StreamProvider {
    private static final Logger log = Logger.getLogger(BaseStreamProvider.class.getName());

    public URLConnection openConnection(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException {
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, String.format("%s opening connection: url [%s], headers [%s], connectTimeout [%s], readTimeout [%s]", getClass().getSimpleName(), url, headers, connectTimeout, readTimeout));
        }
        URLConnection connection = new URL(url).openConnection();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        if (connectTimeout < 0 || readTimeout < 0) {
            throw new IllegalArgumentException(
                String.format("Neither connectTimeout [%s] nor readTimeout [%s] can be less than 0 for URLConnection.", connectTimeout, readTimeout));
        }
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        return connection;
    }

}
