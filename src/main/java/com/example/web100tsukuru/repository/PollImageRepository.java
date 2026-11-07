package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.PollImage;
import com.example.web100tsukuru.repository.PollRepositories.PollImageInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PollImageRepository extends JpaRepository<PollImage, Long> {

    List<PollImageInfo> findByPollIdOrderBySlot(Long pollId);

    // 一覧に出すお題の画像（slot 0）
    List<PollImageInfo> findByPollIdInAndSlot(Collection<Long> pollIds, int slot);

    Optional<PollImage> findByPollIdAndSlot(Long pollId, int slot);

    @Modifying
    @Query("delete from PollImage i where i.pollId in :pollIds")
    int deleteByPollIds(@Param("pollIds") Collection<Long> pollIds);
}
