package org.jgroups.ping.kube.test;

import org.jgroups.protocols.kubernetes.stream.CertificateStreamProvider;
import org.junit.Test;

import java.io.InputStream;

import static org.jgroups.protocols.kubernetes.Utils.getSystemEnv;
import static org.jgroups.protocols.kubernetes.Utils.getSystemProperty;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CertsTest {

    private static String getValue(String name) {
        return getValue(name, null);
    }

    private static String getValue(String name, String defaultValue) {
        String value = getSystemEnv(name);
        return value != null ? value : getSystemProperty(name, defaultValue);
    }

    @Test
    public void testCerts() throws Exception {
        String clientCertFile = getValue("KUBERNETES_CLIENT_CERTIFICATE_FILE");
        String clientKeyFile = getValue("KUBERNETES_CLIENT_KEY_FILE");
        String clientKeyPassword = getValue("KUBERNETES_CLIENT_KEY_PASSWORD");
        String clientKeyAlgo = getValue("KUBERNETES_CLIENT_KEY_ALGO");
        String caCertFile = getValue("KUBERNETES_CA_CERTIFICATE_FILE");

        if (clientCertFile == null) {
            return;
        }

        CertificateStreamProvider certStreamProvider =
                new CertificateStreamProvider(clientCertFile, clientKeyFile, clientKeyPassword, clientKeyAlgo, caCertFile);

        String k8s_master = getValue("KUBERNETES_MASTER");
        String apiVersion = getValue("API_VERSION", "v1beta1");
        String op = getValue("OP", "pods");

        try (InputStream is = certStreamProvider.openStream(String.format("%s/api/%s/%s", k8s_master, apiVersion, op), null, 0, 0)) {
            int x;
            while ((x = is.read()) != -1) {
                System.out.print((char) x);
            }
        }
    }

}
