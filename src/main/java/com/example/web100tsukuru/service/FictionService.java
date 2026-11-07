package com.example.web100tsukuru.service;

import com.example.web100tsukuru.dto.FictionForm;
import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.repository.FictionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FictionService {

    private final FictionRepository fictionRepository;

    // プレースホルダーのパターンを定義します（例: [IMG] [IMG] のように挿入してもらう）
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[IMG\\]");

    // コンストラクタインジェクション (DBリポジトリを自動で使えるようにする)
    public FictionService(FictionRepository fictionRepository) {
        this.fictionRepository = fictionRepository;
    }

    public Fiction saveFiction(FictionForm form) {
        
        // 1. プレースホルダーの数をカウント
        int count = countPlaceholders(form.getBody());

        // 2. DTOをエンティティに変換
        Fiction fiction = new Fiction();
        fiction.setTitle(form.getTitle());
        fiction.setBody(form.getBody());
        fiction.setAuthorName(form.getAuthorName());
        fiction.setPlaceholderCount(count); // カウントした数を設定
        
        // 3. データベースに保存
        return fictionRepository.save(fiction);
    }

    /** 投稿一覧（新しい順） */
    public List<Fiction> findAll() {
        return fictionRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 投稿1件 */
    public Optional<Fiction> findById(Long id) {
        return fictionRepository.findById(id);
    }

    /**
     * 本文を [IMG] で区切った断片に分ける（詳細ページで画像枠を差し込むため）。
     */
    public List<String> splitBody(String body) {
        return List.of(PLACEHOLDER_PATTERN.split(body, -1));
    }

    /**
     * ブログ本文内のプレースホルダー "[IMG]" の数を数える。
     */
    private int countPlaceholders(String body) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(body);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
