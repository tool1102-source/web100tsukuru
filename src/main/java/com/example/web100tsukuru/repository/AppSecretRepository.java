package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.AppSecret;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSecretRepository extends JpaRepository<AppSecret, String> {
}
