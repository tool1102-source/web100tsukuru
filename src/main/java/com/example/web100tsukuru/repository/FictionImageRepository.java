package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.FictionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FictionImageRepository extends JpaRepository<FictionImage, Long> {

    // 記事に並べる画像（中身は読まない）
    List<FictionImageInfo> findByFictionIdOrderByImageNo(Long fictionId);

    // 管理画面の一覧に並べる画像（中身は読まない）
    List<FictionImageInfo> findByFictionIdInOrderByFictionIdAscImageNoAsc(Collection<Long> fictionIds);

    // 画像1枚の中身
    Optional<FictionImage> findByFictionIdAndImageNo(Long fictionId, int imageNo);

    // 投稿を消すときに、その画像もまとめて消す（中身を読み込まずに消す）
    @Modifying
    @Query("delete from FictionImage i where i.fictionId in :fictionIds")
    int deleteByFictionIds(@Param("fictionIds") Collection<Long> fictionIds);
}
