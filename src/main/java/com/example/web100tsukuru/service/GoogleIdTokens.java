package com.example.web100tsukuru.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Optional;
import java.util.Set;

/**
 * 「Google でログイン」で届いた証明書（ID トークン）が本物かを確かめる。
 *
 * ブラウザの「Google でログイン」ボタン（Google Identity Services）を使うので、
 * サーバーに要るのは公開してよいクライアント ID だけ（秘密のキーは要らない）。
 * 証明書は、Google の公開鍵での署名・宛先（クライアント ID）・発行元・期限を確かめる。
 * 受け取るのは、Google アカウントを見分ける番号（sub）だけ。名前やメールアドレスは使わない。
 */
@Component
public class GoogleIdTokens {

    private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    /** 確かめられた Google アカウント */
    public record GoogleIdentity(String subject) {
    }

    private final String clientId;
    private final ConfigurableJWTProcessor<SecurityContext> processor;

    @Autowired
    public GoogleIdTokens(@Value("${app.vote.google-client-id:}") String clientId) throws MalformedURLException {
        // 公開鍵は、はじめて使うときに Google から取ってきて覚えておく（起動のときには取りに行かない）
        this(clientId, JWKSourceBuilder.create(URI.create(GOOGLE_KEYS).toURL()).build());
    }

    GoogleIdTokens(String clientId, JWKSource<SecurityContext> keys) {
        this.clientId = clientId == null ? "" : clientId.strip();
        this.processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                this.clientId, new JWTClaimsSet.Builder().build(), Set.of("sub", "iss", "exp", "aud")));
    }

    /** クライアント ID が設定されていれば、「Google でログイン」を出す */
    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    public String getClientId() {
        return clientId;
    }

    /** 本物の証明書なら、その Google アカウント。偽物・期限切れ・宛先違いなら空 */
    public Optional<GoogleIdentity> verify(String credential) {
        if (!isEnabled() || credential == null || credential.isBlank()) {
            return Optional.empty();
        }
        try {
            JWTClaimsSet claims = processor.process(credential, null);
            if (!ISSUERS.contains(claims.getIssuer()) || claims.getSubject() == null || claims.getSubject().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new GoogleIdentity(claims.getSubject()));
        } catch (ParseException | BadJOSEException | JOSEException e) {
            return Optional.empty();
        }
    }
}
