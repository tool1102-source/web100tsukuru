package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.Fiction;
import org.springframework.data.jpa.repository.JpaRepository;

// このインターフェースを定義するだけで、Spring Bootが自動で
// データベースへの保存、検索などの基本的な機能（CRUD操作）を実装してくれます。
public interface FictionRepository extends JpaRepository<Fiction, Long> {
    
    // 基本的な機能はこれだけでOK
    
}