package org.jgroups.protocols.kubernetes;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class UtilsTest {

    @Test
    public void testSanitizeHttpHeaders() {
        HashMap<String, String> params = new HashMap<>();
        params.put("Host", "jgroups.org");
        params.put("Authorization", "Basic abcd");
        params.put("authorization", "Bearer abcd");

        Map<String, String> sanitized = Utils.sanitizeHttpHeaders(params);
        
        Assertions.assertThat(sanitized.get("Host")).isEqualTo("jgroups.org");
        Assertions.assertThat(sanitized.get("Authorization")).isEqualTo("***");
        Assertions.assertThat(sanitized.get("authorization")).isEqualTo("***");
    }
}
