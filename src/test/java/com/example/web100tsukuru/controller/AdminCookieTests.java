package com.example.web100tsukuru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** ログイン状態の Cookie の付け方は本物のサーバー（Tomcat）でしか確かめられないので、実際に起動して見る */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.admin-password=テスト用の合言葉")
class AdminCookieTests {

    @LocalServerPort
    int port;

    @Test
    void ログインのCookieはよそのサイトから使えず_JavaScriptからも読めない() throws Exception {
        String form = "password=" + URLEncoder.encode("テスト用の合言葉", StandardCharsets.UTF_8);
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/admin/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(302);
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).startsWith("JSESSIONID=").contains("SameSite=Strict").contains("HttpOnly");
        // URL にセッションIDを載せない
        assertThat(response.headers().firstValue("Location").orElseThrow()).doesNotContain("jsessionid");
    }
}
