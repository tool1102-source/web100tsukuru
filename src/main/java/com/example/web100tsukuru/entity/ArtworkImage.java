package com.example.web100tsukuru.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 絵の画像（512×512、13色に減らした PNG）。一覧で絵の行を読むたびに画像まで読まないよう、別の表に置く */
@Entity
public class ArtworkImage {

    // 絵の番号と同じ
    @Id
    private Long artworkId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] png;

    protected ArtworkImage() {
    }

    public ArtworkImage(Long artworkId, byte[] png) {
        this.artworkId = artworkId;
        this.png = png;
    }

    public Long getArtworkId() {
        return artworkId;
    }

    public byte[] getPng() {
        return png;
    }
}
