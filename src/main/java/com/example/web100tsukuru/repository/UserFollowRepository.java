package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    // フォローしている人数・フォロワーの人数
    long countByFollowerId(Long followerId);

    long countByFolloweeId(Long followeeId);

    // フォローしている人（新しくフォローした順）
    List<UserFollow> findByFollowerIdOrderByCreatedAtDescIdDesc(Long followerId);

    @Modifying
    @Query("delete from UserFollow f where f.followerId = :followerId and f.followeeId = :followeeId")
    int deletePair(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);
}
