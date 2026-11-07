package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.repository.FictionImageRepository;
import com.example.web100tsukuru.repository.FictionRepository;
import com.example.web100tsukuru.service.FictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FictionImageTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    FictionRepository repository;

    @Autowired
    FictionImageRepository imageRepository;

    @Autowired
    FictionService fictionService;

    @BeforeEach
    void clean() {
        imageRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void 画像はIMGの位置に入り_IMGより多い分は最後に並ぶ() throws Exception {
        Long id = post("前の段落[IMG]後ろの段落",
                image("a.png", "png", 200, 100, Color.RED),
                image("b.png", "png", 100, 100, Color.BLUE));

        String page = mvc.perform(get("/fiction/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // 記事の本文の部分だけを見る（head の説明文にも本文の冒頭が入るため）
        String html = page.substring(page.indexOf("class=\"article-body\""));
        int before = html.indexOf("前の段落");
        int first = html.indexOf("/fiction/" + id + "/images/1.jpg");
        int after = html.indexOf("後ろの段落");
        int second = html.indexOf("/fiction/" + id + "/images/2.jpg");
        assertThat(before).isPositive();
        assertThat(first).isGreaterThan(before);
        assertThat(after).isGreaterThan(first);
        assertThat(second).isGreaterThan(after);
        assertThat(html).contains("width=\"200\" height=\"100\"").doesNotContain("[IMG]");
    }

    @Test
    void 画像は長い辺1600pxまでのJPEGにし直し_写真の付属データは残さない() throws Exception {
        Long id = post("[IMG]", withExif(image("photo.jpg", "jpg", 3200, 800, Color.GREEN)));

        MvcResult result = mvc.perform(get("/fiction/" + id + "/images/1.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                // 消した画像が中継サーバーから配られ続けないように、覚えてよいのは見た人のブラウザだけ
                .andExpect(header().string("Cache-Control", "max-age=86400, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        byte[] jpeg = result.getResponse().getContentAsByteArray();
        BufferedImage saved = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(saved.getWidth()).isEqualTo(1600);
        assertThat(saved.getHeight()).isEqualTo(400);
        assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
    }

    @Test
    void 画像でないファイルは受け付けず_何も保存しない() throws Exception {
        mvc.perform(form("本文", new MockMultipartFile("images", "memo.txt", "text/plain", "こんにちは".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("「memo.txt」は画像として読めませんでした")));
        assertThat(repository.count()).isZero();
        assertThat(imageRepository.count()).isZero();
    }

    @Test
    void 画像は5枚まで() throws Exception {
        MockMultipartFile[] six = new MockMultipartFile[6];
        for (int i = 0; i < six.length; i++) {
            six[i] = image("p" + i + ".png", "png", 10, 10, Color.GRAY);
        }
        mvc.perform(form("本文", six))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("画像は5枚までです。")));
        assertThat(repository.count()).isZero();
    }

    @Test
    void 画像を選ばずに投稿しても今までどおり保存できる() throws Exception {
        Long id = post("画像なし", new MockMultipartFile("images", "", "application/octet-stream", new byte[0]));
        assertThat(imageRepository.count()).isZero();
        mvc.perform(get("/fiction/" + id)).andExpect(content().string(containsString("画像なし")));
    }

    @Test
    void 投稿を消すと画像も消える() throws Exception {
        Long id = post("[IMG]", image("a.png", "png", 20, 20, Color.RED));
        assertThat(imageRepository.count()).isEqualTo(1);
        List<Fiction> deleted = fictionService.deleteAll(List.of(id));
        assertThat(deleted).hasSize(1);
        assertThat(imageRepository.count()).isZero();
        mvc.perform(get("/fiction/" + id + "/images/1.jpg")).andExpect(status().isNotFound());
    }

    @Test
    void ない画像は404() throws Exception {
        Long id = post("画像なし");
        mvc.perform(get("/fiction/" + id + "/images/1.jpg")).andExpect(status().isNotFound());
        mvc.perform(get("/fiction/99999/images/1.jpg")).andExpect(status().isNotFound());
    }

    private Long post(String body, MockMultipartFile... images) throws Exception {
        String location = mvc.perform(form(body, images))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/fiction/*"))
                .andReturn().getResponse().getRedirectedUrl();
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private static MockMultipartHttpServletRequestBuilder form(String body, MockMultipartFile... images) {
        MockMultipartHttpServletRequestBuilder request = multipart("/fiction");
        for (MockMultipartFile image : images) {
            request.file(image);
        }
        request.param("title", "画像つきの話").param("authorName", "テスト").param("body", body);
        return request;
    }

    private static MockMultipartFile image(String name, String format, int width, int height, Color color) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, format, bytes);
        return new MockMultipartFile("images", name, "image/" + ("jpg".equals(format) ? "jpeg" : format), bytes.toByteArray());
    }

    /** JPEG の先頭に、スマホの写真のような付属データ（Exif）の区画を差し込む */
    private static MockMultipartFile withExif(MockMultipartFile jpeg) throws IOException {
        byte[] original = jpeg.getBytes();
        byte[] payload = "Exif\0\0GPS-35.6812,139.7671".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(original, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1); // APP1
        int length = payload.length + 2;
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(payload);
        out.write(original, 2, original.length - 2);
        return new MockMultipartFile(jpeg.getName(), jpeg.getOriginalFilename(), jpeg.getContentType(), out.toByteArray());
    }
}
