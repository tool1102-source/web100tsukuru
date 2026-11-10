package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.ArtworkReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtworkReportRepository extends JpaRepository<ArtworkReport, Long> {

    boolean existsByArtworkIdAndUserId(Long artworkId, Long userId);

    void deleteByArtworkId(Long artworkId);
}
