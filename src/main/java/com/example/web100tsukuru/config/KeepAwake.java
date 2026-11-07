package com.example.web100tsukuru.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Render の無料プランで、サイトが休止しないようにする。
 *
 * Render の無料プランは、15分ほど訪問がないとサイトを休止させる。
 * 休止したサイトは次の訪問で起こされるはずだが、このサイトは起こすときに失敗して
 * 503（x-render-routing: hibernate-wake-error）を返し続けることがあった。
 * そこで、10分おきに自分のトップページを外のインターネット経由で見に行き、休止させない。
 * トップページは DB を使わないので、Neon の DB はこれでは起きない。
 *
 * 動くのは Render の上だけ（Render が入れる環境変数 RENDER=true のとき）。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "RENDER", havingValue = "true")
public class KeepAwake {

    private static final Logger log = LoggerFactory.getLogger(KeepAwake.class);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final URI target;

    public KeepAwake(@Value("${app.base-url}") String baseUrl) {
        this.target = URI.create(baseUrl.replaceAll("/+$", "") + "/");
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
    public void ping() {
        HttpRequest request = HttpRequest.newBuilder(target)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "web100tsukuru-keep-awake")
                .build();
        try {
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 400) {
                log.warn("休止させないための自己アクセスで {} が返りました（{}）", status, target);
            }
        } catch (Exception e) {
            log.warn("休止させないための自己アクセスに失敗しました（{}）: {}", target, e.toString());
        }
    }
}
