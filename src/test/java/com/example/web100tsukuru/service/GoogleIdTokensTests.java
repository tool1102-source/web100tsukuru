package com.example.web100tsukuru.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** Google の代わりに、テストの中で作った鍵で証明書に署名して確かめる（ネットにはつながない） */
class GoogleIdTokensTests {

    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
    private static RSAKey googleKey;
    private static RSAKey otherKey;
    private static GoogleIdTokens tokens;

    @BeforeAll
    static void keys() throws Exception {
        googleKey = new RSAKeyGenerator(2048).keyID("google-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("google-key").generate();
        tokens = new GoogleIdTokens(CLIENT_ID, new ImmutableJWKSet<>(new JWKSet(googleKey.toPublicJWK())));
    }

    @Test
    void 本物の証明書ならGoogleアカウントの番号が取れる() throws Exception {
        assertThat(tokens.verify(token(googleKey, CLIENT_ID, "https://accounts.google.com", 600)))
                .hasValueSatisfying(id -> assertThat(id.subject()).isEqualTo("google-sub-123"));
        assertThat(tokens.verify(token(googleKey, CLIENT_ID, "accounts.google.com", 600))).isPresent();
    }

    @Test
    void 別のアプリあての証明書は受け付けない() throws Exception {
        assertThat(tokens.verify(token(googleKey, "other-app.apps.googleusercontent.com", "https://accounts.google.com", 600))).isEmpty();
    }

    @Test
    void 期限切れの証明書は受け付けない() throws Exception {
        assertThat(tokens.verify(token(googleKey, CLIENT_ID, "https://accounts.google.com", -600))).isEmpty();
    }

    @Test
    void Google以外が出した証明書は受け付けない() throws Exception {
        assertThat(tokens.verify(token(googleKey, CLIENT_ID, "https://evil.example", 600))).isEmpty();
    }

    @Test
    void Googleの鍵で署名されていない証明書は受け付けない() throws Exception {
        assertThat(tokens.verify(token(otherKey, CLIENT_ID, "https://accounts.google.com", 600))).isEmpty();
        assertThat(tokens.verify("これは証明書ではない")).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
    }

    @Test
    void クライアントIDがなければGoogleでログインは出さない() throws Exception {
        GoogleIdTokens disabled = new GoogleIdTokens("", new ImmutableJWKSet<>(new JWKSet(googleKey.toPublicJWK())));
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.verify(token(googleKey, CLIENT_ID, "https://accounts.google.com", 600))).isEmpty();
    }

    private static String token(RSAKey key, String audience, String issuer, long secondsUntilExpiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("google-sub-123")
                .issuer(issuer)
                .audience(audience)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(secondsUntilExpiry)))
                .claim("name", "本名 太郎")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
