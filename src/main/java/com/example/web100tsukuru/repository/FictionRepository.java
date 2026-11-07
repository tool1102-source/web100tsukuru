package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.Fiction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FictionRepository extends JpaRepository<Fiction, Long> {

    // 新しい投稿から順に、ページ単位で取得
    Page<Fiction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
