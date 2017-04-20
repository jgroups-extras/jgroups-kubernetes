package org.jgroups.protocols.kubernetes.stream;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Callable;

public class OpenStream implements Callable<InputStream> {

    private final StreamProvider streamProvider;
    private final String url;
    private final Map<String, String> headers;
    private final int connectTimeout;
    private final int readTimeout;

    public OpenStream(StreamProvider streamProvider, String url, Map<String, String> headers, int connectTimeout, int readTimeout) {
        this.streamProvider = (streamProvider != null) ? streamProvider : new DefaultStreamProvider();
        this.url = url;
        this.headers = headers;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public InputStream call() throws Exception {
        return streamProvider.openStream(url, headers, connectTimeout, readTimeout);
    }

}
