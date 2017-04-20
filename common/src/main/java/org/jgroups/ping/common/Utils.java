
package org.jgroups.ping.common;

import org.jgroups.ping.common.stream.OpenStream;
import org.jgroups.ping.common.stream.StreamProvider;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public final class Utils {
    private static final Logger log = Logger.getLogger(Utils.class.getName());

    public static final InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout, int attempts, long sleep, StreamProvider streamProvider) throws Exception {
        return execute(new OpenStream(streamProvider, url, headers, connectTimeout, readTimeout), attempts, sleep, true);
    }

    public static final InputStream openFile(String name) throws FileNotFoundException {
        if (name != null) {
            return new BufferedInputStream(new FileInputStream(name));
        }
        return null;
    }

    public static final String readFileToString(String name) throws IOException {
        return name != null? readFileToString(new File(name)) : null;
    }

    public static final String readFileToString(File file) throws IOException {
        if (file != null && file.canRead()) {
            Path path = FileSystems.getDefault().getPath(file.getCanonicalPath());
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes);
        }
        return null;
    }


    public static final String getSystemProperty(final String key, final String def) {
        return getSystemProperty(key, def, false);
    }

    public static final String getSystemProperty(final String key, final String def, final boolean trimToNull) {
        if (key != null) {
            String val = AccessController.doPrivileged((PrivilegedAction<String>)() -> System.getProperty(key));
            if (trimToNull) {
                val = trimToNull(val);
            }
            if (val != null) {
                return val;
            }
        }
        return def;
    }


    public static final String getSystemEnv(final String key) {
        return getSystemEnv(key, null);
     }

    public static final String getSystemEnv(final String key, final String def) {
        return getSystemEnv(key, def, false);
    }

    public static final String getSystemEnv(final String key, final String def, final boolean trimToNull) {
        if (key != null) {
            String val = AccessController.doPrivileged((PrivilegedAction<String>)() -> System.getenv(key));
            if (trimToNull) {
                val = trimToNull(val);
            }
            if (val != null) {
                return val;
            }
        }
        return def;
    }

    public static final String trimToNull(String s) {
        if (s != null) {
            s = s.trim();
            if (s.isEmpty()) {
                s = null;
            }
        }
        return s;
    }

    public static final String urlencode(String s) {
        try {
            return s != null ? URLEncoder.encode(s, "UTF-8") : null;
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(String.format("Could not encode String [%s] as UTF-8 (which should always be supported).", s), uee);
        }
    }

    public static final <V> V execute(Callable<V> callable, int attempts, long sleep) {
        try {
            return execute(callable, attempts, sleep, false);
        } catch (Exception e) {
            // exception message logged in overloaded method below
            return null;
        }
    }

    public static final <V> V execute(Callable<V> callable, int attempts, long sleep, boolean throwOnFail) throws Exception {
        V value = null;
        int tries = attempts;
        Throwable lastFail = null;
        while (tries > 0) {
            tries--;
            try {
               value = callable.call();
               if (value != null) {
                   lastFail = null;
                   break;
               }
            } catch (Throwable fail) {
                lastFail = fail;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (lastFail != null && (throwOnFail || log.isLoggable(Level.INFO))) {
            String emsg = String.format("%s attempt(s) with a %sms sleep to execute [%s] failed. Last failure was [%s: %s]",
                    attempts, sleep, callable.getClass().getSimpleName(), lastFail.getClass().getName(), lastFail.getMessage());
            if (throwOnFail) {
                throw new Exception(emsg, lastFail);
            } else {
                log.info(emsg);
            }
        }
        return value;
    }

    private Utils() {}
}
