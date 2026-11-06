package org.bank.common.dto.response;

/**
 * @param accessToken  signed RS256 JWT
 * @param tokenType    always "Bearer"
 * @param expiresIn    access token lifetime in seconds
 * @param refreshToken opaque rotating token; only its hash is stored server-side
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds, String refreshToken) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds, refreshToken);
    }

    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", tokens=***]";
    }
}
