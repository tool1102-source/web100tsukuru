package com.example.web100tsukuru.service.auction;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** X などで共有したときの画像（1200×630）：壁に掛けた金の額縁に、絵を入れる */
public final class FramedImage {

    public static final int WIDTH = 1200;
    public static final int HEIGHT = 630;

    private FramedImage() {
    }

    public static byte[] render(byte[] png) throws IOException {
        BufferedImage art = ImageIO.read(new ByteArrayInputStream(png));
        if (art == null) {
            throw new IOException("絵を読めませんでした");
        }
        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 壁（上が少し明るい）と、床の帯
        g.setPaint(new GradientPaint(0, 0, new Color(0x6e1f27), 0, HEIGHT, new Color(0x4a141a)));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(0x3a0f14));
        g.fillRect(0, HEIGHT - 40, WIDTH, 40);

        int outer = 548;
        int x = (WIDTH - outer) / 2;
        int y = (HEIGHT - 40 - outer) / 2 + 8;
        // 影
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(x + 10, y + 14, outer, outer);
        // 金の額縁（外から内へ、明るさを変えて立体に見せる）
        int[] golds = {0x8a6420, 0xc79a3c, 0xe8c873, 0xb98a30, 0x7d5a1c};
        int band = 26 / golds.length + 1;
        for (int i = 0; i < golds.length; i++) {
            g.setColor(new Color(golds[i]));
            int inset = i * band;
            g.fillRect(x + inset, y + inset, outer - inset * 2, outer - inset * 2);
        }
        // 台紙
        int frame = 28;
        g.setColor(new Color(0xfbf8f1));
        g.fillRect(x + frame, y + frame, outer - frame * 2, outer - frame * 2);
        // 絵（ドットがにじまないように、拡大は近い画素で）
        int mat = 30;
        int size = outer - (frame + mat) * 2;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(art, x + frame + mat, y + frame + mat, size, size, null);
        g.setColor(new Color(0, 0, 0, 40));
        g.drawRect(x + frame + mat - 1, y + frame + mat - 1, size + 1, size + 1);
        g.dispose();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.9f);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(out, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
