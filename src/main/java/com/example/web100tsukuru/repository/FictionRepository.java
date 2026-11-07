package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.Fiction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FictionRepository extends JpaRepository<Fiction, Long> {

    // 新しい投稿から順に全件取得
    List<Fiction> findAllByOrderByCreatedAtDesc();
}
