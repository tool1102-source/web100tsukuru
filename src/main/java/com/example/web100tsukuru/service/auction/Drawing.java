package com.example.web100tsukuru.service.auction;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 描く画面から届いた PNG を読み、13色の絵に直して特徴を測る。
 * 写真などを貼られても13色の 512×512 に直すので、保存されるのは「描いた絵」の形だけになる。
 */
public final class Drawing {

    public static final int SIZE = 512;

    /** 絵の特徴。share は色ごとの割合（塗った部分に対して。添字はパレットの番号） */
    public record Features(double ink, double detail, int colors, double[] share) {

        public double shareOf(int... indexes) {
            double sum = 0;
            for (int i : indexes) {
                sum += share[i];
            }
            return sum;
        }
    }

    public record Result(Features features, byte[] png) {
    }

    public static class InvalidDrawingException extends Exception {
        public InvalidDrawingException(String message) {
            super(message);
        }
    }

    private Drawing() {
    }

    public static Result read(byte[] data) throws InvalidDrawingException {
        BufferedImage image;
        try {
            image = data == null || data.length == 0 ? null : ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException | RuntimeException e) {
            image = null;
        }
        if (image == null) {
            throw new InvalidDrawingException("絵を読み取れませんでした。もう一度お試しください。");
        }
        if (image.getWidth() != SIZE || image.getHeight() != SIZE) {
            throw new InvalidDrawingException("絵の大きさが違います。描く画面からお試しください。");
        }
        int[] argb = image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
        byte[] index = new byte[SIZE * SIZE];
        java.util.Map<Integer, Integer> cache = new java.util.HashMap<>();
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int alpha = (p >>> 24) & 0xff;
            int rgb = alpha < 128 ? 0xffffff : p & 0xffffff;
            index[i] = (byte) (int) cache.computeIfAbsent(rgb, Palette::nearest);
        }
        return new Result(measure(index), encode(index));
    }

    /** 塗った広さ・細かさ（色の境目の多さ）・色の割合を測る */
    static Features measure(byte[] index) {
        int[] counts = new int[Palette.SIZE];
        int edges = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                int c = index[i];
                counts[c]++;
                boolean edge = (x + 1 < SIZE && index[i + 1] != c) || (y + 1 < SIZE && index[i + SIZE] != c);
                if (edge) {
                    edges++;
                }
            }
        }
        int total = SIZE * SIZE;
        int inked = total - counts[0];
        double[] share = new double[Palette.SIZE];
        int colors = 0;
        for (int c = 1; c < Palette.SIZE; c++) {
            share[c] = inked == 0 ? 0 : (double) counts[c] / inked;
            if (share[c] >= 0.02) {
                colors++;
            }
        }
        return new Features((double) inked / total, (double) edges / total, colors, share);
    }

    /** 16色の枠の PNG にする（13色しか使わないので、元の PNG よりずっと小さくなる） */
    static byte[] encode(byte[] index) {
        byte[] r = new byte[16];
        byte[] g = new byte[16];
        byte[] b = new byte[16];
        for (Palette.Color c : Palette.COLORS) {
            r[c.index()] = (byte) ((c.rgb() >> 16) & 0xff);
            g[c.index()] = (byte) ((c.rgb() >> 8) & 0xff);
            b[c.index()] = (byte) (c.rgb() & 0xff);
        }
        IndexColorModel model = new IndexColorModel(4, 16, r, g, b);
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_BINARY, model);
        WritableRaster raster = out.getRaster();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                raster.setSample(x, y, 0, index[y * SIZE + x]);
            }
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
