package com.example.web100tsukuru.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlEnvironmentPostProcessorTests {

    @Test
    void renderの内部URLを変換できる() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties(
                "postgresql://web100user:abcDEF123@dpg-xxxx-a/web100db");

        assertThat(p.get("spring.datasource.url")).isEqualTo("jdbc:postgresql://dpg-xxxx-a:5432/web100db");
        assertThat(p.get("spring.datasource.username")).isEqualTo("web100user");
        assertThat(p.get("spring.datasource.password")).isEqualTo("abcDEF123");
    }

    @Test
    void ポート指定と記号入りパスワードとクエリも扱える() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties(
                "postgres://u:p%40ss%3Aword@db.example.com:6543/mydb?sslmode=require");

        assertThat(p.get("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.example.com:6543/mydb?sslmode=require");
        assertThat(p.get("spring.datasource.password")).isEqualTo("p@ss:word");
    }

    @Test
    void postgresql以外はエラーにする() {
        assertThatThrownBy(() -> DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties("mysql://u:p@h/db"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
