package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.PollImage;
import com.example.web100tsukuru.service.PollService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * 「ちいさな投票所」の画像 (GET /vote/{id}/images/{slot}.jpg)。
 * 画面と別にしてあるのは、画像を出すたびに締め切りの後かたづけ（VoteModelAdvice）をしないため。
 * フィクションブログの画像と同じく、見た人のブラウザにだけ1日覚えさせる。
 */
@RestController
public class PollImageController {

    private final PollService polls;

    public PollImageController(PollService polls) {
        this.polls = polls;
    }

    @GetMapping("/vote/{id:\\d+}/images/{slot:\\d+}.jpg")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable int slot) {
        PollImage image = polls.findImage(id, slot)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.getData());
    }
}
