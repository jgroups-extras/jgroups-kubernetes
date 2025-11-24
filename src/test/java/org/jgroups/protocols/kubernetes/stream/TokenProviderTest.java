package org.jgroups.protocols.kubernetes.stream;

import mjson.Json;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

import static org.jgroups.protocols.kubernetes.Utils.readFileToString;
import static org.junit.Assert.*;

public class TokenProviderTest
{
    private static final String JWT_PREFIX = "eyJhbGciOiJIUzI1NiJ9.";
    private String validJwtWithExpiry;
    private String validJwtWithoutExpiry;
    private static final String INVALID_JWT = "invalid.jwt.token";
    private File tempTokenFile;
    private TokenProvider tokenProvider;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        validJwtWithExpiry = readFileToString(new File(TokenProvider.class.getResource("/tokenWithExpiry.txt").toURI()));
        validJwtWithoutExpiry = readFileToString(new File(TokenProvider.class.getResource("/tokenWithoutExpiry.txt").toURI()));
        tempTokenFile = File.createTempFile("token", ".txt");
        tempTokenFile.deleteOnExit();
        tokenProvider = new TokenProvider(tempTokenFile.getAbsolutePath());
    }

    @Test
    public void returnsTokenWhenFileContainsValidJWT() throws IOException {
        Files.writeString(tempTokenFile.toPath(), validJwtWithExpiry);
        String token = tokenProvider.getToken();
        assertEquals(validJwtWithExpiry, token);
    }

    @Test
    public void throwsIOExceptionWhenFileContainsInvalidJWT() throws IOException {
        Files.writeString(tempTokenFile.toPath(), INVALID_JWT);
        assertThrows(IOException.class, () -> tokenProvider.getToken());
    }

    @Test
    public void returnsSameTokenIfNotExpired() throws IOException {
        Files.writeString(tempTokenFile.toPath(), validJwtWithExpiry);
        String token1 = tokenProvider.getToken();
        String token2 = tokenProvider.getToken();
        assertEquals(token1, token2);
    }

    @Test
    public void refreshesTokenWhenExpired() throws IOException, InterruptedException {
        Files.writeString(tempTokenFile.toPath(), validJwtWithExpiry);
        updateTokenExpiryInFile(tempTokenFile, (System.currentTimeMillis() + 1000) / 1000); // Set expiry in the past
        String token1 = tokenProvider.getToken();
        Thread.sleep(2000L);
        updateTokenExpiryInFile(tempTokenFile, (System.currentTimeMillis() + 1000) / 1000); // Set expiry in the past
        String toekn2 = tokenProvider.getToken();
        assertNotEquals(token1, toekn2);
    }

    @Test
    public void throwsIOExceptionForInvalidJWTExpiry() {
        assertThrows(IOException.class, () -> TokenProvider.getExpiry(INVALID_JWT));
    }

    @Test
    public void returnsExpiryForValidJWT() throws IOException {
        Files.writeString(tempTokenFile.toPath(), validJwtWithExpiry);
        long tokenExpiry = (System.currentTimeMillis() + 10000) / 1000;
        updateTokenExpiryInFile(tempTokenFile, tokenExpiry); // JWT 'exp' is in seconds
        long expiry = TokenProvider.getExpiry(tokenProvider.getToken());
        assertEquals(tokenExpiry, expiry);
    }

    @Test
    public void returnsMinusOneForJWTWithoutExpiry() throws IOException {
        Files.writeString(tempTokenFile.toPath(), validJwtWithoutExpiry);
        long expiry = TokenProvider.getExpiry(tokenProvider.getToken());
        assertEquals(-1, expiry);
    }

    public static void updateTokenExpiryInFile(File tokenFile, long newExpiry) throws IOException {
        String token = Files.readString(tokenFile.toPath()).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 3) throw new IOException("Invalid JWT token format");

        // Decode payload
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Json payload = Json.read(payloadJson);

        // Update expiry
        payload.set("exp", newExpiry);

        // Re-encode payload
        String newPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));

        // Reconstruct token
        String newToken = parts[0] + "." + newPayload + "." + parts[2];

        // Save to file
        Files.write(tokenFile.toPath(), newToken.getBytes(StandardCharsets.UTF_8));
    }

}

