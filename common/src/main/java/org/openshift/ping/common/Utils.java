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
package org.openshift.ping.common;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
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

import org.openshift.ping.common.stream.OpenStream;
import org.openshift.ping.common.stream.StreamProvider;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public final class Utils {
    private static final Logger log = Logger.getLogger(Utils.class.getName());

    public static final InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout, int attempts, long sleep) throws Exception {
        return openStream(url, headers, connectTimeout, readTimeout, attempts, sleep, null);
    }

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
        if (name != null) {
            return readFileToString(new File(name));
        }
        return null;
    }

    public static final String readFileToString(File file) throws IOException {
        if (file != null && file.canRead()) {
            Path path = FileSystems.getDefault().getPath(file.getCanonicalPath());
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes);
        }
        return null;
    }

    public static final String getSystemProperty(final String key) {
        return getSystemProperty(key, null);
     }

    public static final String getSystemProperty(final String key, final String def) {
        return getSystemProperty(key, def, false);
    }

    public static final String getSystemProperty(final String key, final String def, final boolean trimToNull) {
        if (key != null) {
            String val = AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    return System.getProperty(key);
                }
            });
            if (trimToNull) {
                val = trimToNull(val);
            }
            if (val != null) {
                return val;
            }
        }
        return def;
    }

    public static final String getSystemProperty(final String[] keys, final String def, final boolean trimToNull) {
        if (keys != null) {
            for (String key : keys) {
                String val = getSystemProperty(key, null, trimToNull);
                if (val != null) {
                    return val;
                }
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
            String val = AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    return System.getenv(key);
                }
            });
            if (trimToNull) {
                val = trimToNull(val);
            }
            if (val != null) {
                return val;
            }
        }
        return def;
    }

    public static final String getSystemEnv(final String[] keys, final String def, final boolean trimToNull) {
        if (keys != null) {
            for (String key : keys) {
                String val = getSystemEnv(key, null, trimToNull);
                if (val != null) {
                    return val;
                }
            }
        }
        return def;
    }

    public static final int getSystemEnvInt(final String key) {
        return getSystemEnvInt(key, 0);
    }

    public static final int getSystemEnvInt(final String key, final int def) {
        return getSystemEnvInt(new String[]{key}, def);
    }

    public static final int getSystemEnvInt(final String[] keys, final int def) {
        if (keys != null) {
            for (String key : keys) {
                String val = getSystemEnv(key, null, true);
                if (val != null) {
                    try {
                        return Integer.parseInt(val);
                    } catch (NumberFormatException nfe) {
                        if (log.isLoggable(Level.WARNING)) {
                            log.log(Level.WARNING, String.format("system environment variable [%s] with value [%s] is not an integer: %s", key, val, nfe.getMessage()));
                        }
                    }
                }
            }
        }
        return def;
    }

    public static final String trimToNull(String s) {
        if (s != null) {
            s = s.trim();
            if (s.length() == 0) {
                s = null;
            }
        }
        return s;
    }

    public static final String urlencode(String s) {
        try {
            return s != null ? URLEncoder.encode(s, "UTF-8") : null;
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(String.format("Could not encode String [%s] as UTF-8 (which should always be supported)."), uee);
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
