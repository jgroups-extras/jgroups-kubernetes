package org.jgroups.protocols.kubernetes;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Map;

public class UtilsTest {

    @Test
    public void testSanitizeHttpHeaders() {
        Map<String, String> sanitized = Utils.sanitizeHttpHeaders(Map.of(
                "Host", "jgroups.org",
                "Authorization", "Basic abcd",
                "authorization", "Bearer abcd"
        ));
        Assertions.assertThat(sanitized.get("Host")).isEqualTo("jgroups.org");
        Assertions.assertThat(sanitized.get("Authorization")).isEqualTo("***");
        Assertions.assertThat(sanitized.get("authorization")).isEqualTo("***");
    }
}
