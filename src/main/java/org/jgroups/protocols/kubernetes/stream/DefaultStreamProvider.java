package org.jgroups.protocols.kubernetes.stream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultStreamProvider extends BaseStreamProvider {
    private static final Logger log = Logger.getLogger(DefaultStreamProvider.class.getName());

    public DefaultStreamProvider() {}

    @Override
    public InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException {
        URLConnection connection = openConnection(url, headers, connectTimeout, readTimeout);
        if (log.isLoggable(Level.FINE)) {
            log.fine(String.format("Using URLConnection for url [%s].", url));
        }
        return connection.getInputStream();
    }

}
