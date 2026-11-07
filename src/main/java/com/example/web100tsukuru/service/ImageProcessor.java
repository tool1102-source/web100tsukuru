package com.example.web100tsukuru.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 投稿された画像を読み込み、長い辺 LONG_SIDE px までに縮めて JPEG にし直す。
 *
 * 読めないファイル（画像でないもの）はここで断る。
 * JPEG にし直すので、写真に付いている撮影場所（位置情報）などの付属データは残らない。
 * 無料プランの小さなサーバーなので、大きすぎる画像は断り、読み込むときも間引いてメモリを抑える。
 * 同時に何枚も読むとメモリが足りなくなるので、1枚ずつ順番に処理する（synchronized）。
 */
@Component
public class ImageProcessor {

    public static final int LONG_SIDE = 1600;
    private static final int MAX_SIDE = 10_000;
    private static final long MAX_PIXELS = 50_000_000L;
    private static final float JPEG_QUALITY = 0.85f;

    static {
        // 一時ファイルを作らず、メモリの上で読み書きする
        ImageIO.setUseCache(false);
    }

    /** 縮めた JPEG と、その幅・高さ */
    public record ProcessedImage(byte[] jpeg, int width, int height) {
    }

    /** 画像として受け付けられないとき。メッセージはそのまま画面に出す */
    public static class InvalidImageException extends Exception {
        public InvalidImageException(String message) {
            super(message);
        }
    }

    public synchronized ProcessedImage process(MultipartFile file) throws InvalidImageException {
        String name = file.getOriginalFilename() == null ? "画像" : file.getOriginalFilename();
        try (InputStream raw = file.getInputStream();
             ImageInputStream in = ImageIO.createImageInputStream(raw)) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new InvalidImageException("「" + name + "」は画像として読めませんでした。JPEG か PNG の画像を選んでください。");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                    throw new InvalidImageException("「" + name + "」は大きすぎます。小さくしてから選んでください。");
                }
                // 大きい画像は、読み込むときに間引いておく（仕上げはあとでなめらかに縮める）
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / (LONG_SIDE + LONG_SIDE / 2));
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage source = reader.read(0, param);
                BufferedImage resized = resize(source);
                return new ProcessedImage(toJpeg(resized), resized.getWidth(), resized.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException("「" + name + "」は画像として読めませんでした。JPEG か PNG の画像を選んでください。");
        }
    }

    /** 長い辺が LONG_SIDE を超えていれば縮める。透明な部分は白にする */
    private static BufferedImage resize(BufferedImage source) {
        double scale = Math.min(1.0, (double) LONG_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        // 一度に大きく縮めると粗くなるので、半分ずつ縮めてから仕上げる
        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return draw(current, targetWidth, targetHeight);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
