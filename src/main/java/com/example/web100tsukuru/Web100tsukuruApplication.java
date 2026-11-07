package com.example.web100tsukuru;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
public class Web100tsukuruApplication {

	/** サイト全体で使うタイムゾーン（Render のサーバーは UTC なので明示する） */
	public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
		SpringApplication.run(Web100tsukuruApplication.class, args);
	}

}
