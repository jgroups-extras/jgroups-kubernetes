package org.jgroups.protocols.kubernetes.stream;

import mjson.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

import static org.jgroups.protocols.kubernetes.Utils.readFileToString;

public class TokenProvider
{
    private static final Logger log = Logger.getLogger(TokenProvider.class.getName());
    private final String tokenFile;
    private volatile long tokenExpiry;
    private volatile String token;

    public TokenProvider(String tokenFile) {
        this.tokenFile = tokenFile;
    }

    public String getToken() throws IOException {
        long currentTime = System.currentTimeMillis() / 1000;
        if (token == null || (tokenExpiry > 0 && tokenExpiry < currentTime)) {
            synchronized (this) {
                if (token == null || (tokenExpiry > 0 && tokenExpiry < currentTime)) {
                    log.info("Refreshing token from file " + tokenFile);
                    token = readFileToString(tokenFile).trim();
                    tokenExpiry = getExpiry(token);
                }
            }
        }
        return token;
    }

    static long getExpiry(String jwtToken) throws IOException {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) throw new IOException("Invalid JWT token");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Json payload = Json.read(payloadJson);
            if (payload.has("exp")) return payload.at("exp").asLong();
            log.info("No 'exp' claim found.");
        } catch (Exception e) {
            throw  new IOException("Error decoding JWT: " + e.getMessage());
        }
        return -1;
    }
}
