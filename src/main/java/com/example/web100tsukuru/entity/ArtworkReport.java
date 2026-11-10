package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/** 不適切な絵の報告（同じ人が同じ絵を何度も報告しても1回と数える） */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "artwork_report_once", columnNames = {"artworkId", "userId"}))
public class ArtworkReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long artworkId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    protected ArtworkReport() {
    }

    public ArtworkReport(Long artworkId, Long userId) {
        this.artworkId = artworkId;
        this.userId = userId;
    }

    public Long getArtworkId() {
        return artworkId;
    }
}
