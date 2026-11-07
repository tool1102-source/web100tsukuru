package com.example.web100tsukuru.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 環境変数 DATABASE_URL（Render の「Internal Database URL」をそのまま貼ったもの）を読み、
 * Spring Boot の接続設定（spring.datasource.*）に変換する。
 *
 * 例: postgresql://ユーザー:パスワード@ホスト名/web100db
 *   → url      = jdbc:postgresql://ホスト名:5432/web100db
 *     username = ユーザー
 *     password = パスワード
 *
 * DATABASE_URL が無いときは何もしない（DB_URL / DB_USERNAME / DB_PASSWORD を使う）。
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        environment.getPropertySources().addFirst(
                new MapPropertySource("renderDatabaseUrl", toDatasourceProperties(databaseUrl.trim())));
    }

    static Map<String, Object> toDatasourceProperties(String databaseUrl) {
        URI uri = URI.create(databaseUrl);
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalArgumentException(
                    "DATABASE_URL は postgresql:// で始まる必要があります（Render の Internal Database URL を貼ってください）");
        }

        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
        if (uri.getRawQuery() != null) {
            jdbcUrl += "?" + uri.getRawQuery();
        }

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbcUrl);

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
            props.put("spring.datasource.username", decode(user));
            if (colon >= 0) {
                props.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
            }
        }
        return props;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
