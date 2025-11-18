package com.example.web100tsukuru.service;

import com.example.web100tsukuru.dto.FictionForm;
import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.repository.FictionRepository;
import org.springframework.stereotype.Service;

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

    public void saveFiction(FictionForm form) {
        
        // 1. プレースホルダーの数をカウント
        int count = countPlaceholders(form.getBody());

        // 2. DTOをエンティティに変換
        Fiction fiction = new Fiction();
        fiction.setTitle(form.getTitle());
        fiction.setBody(form.getBody());
        fiction.setAuthorName(form.getAuthorName());
        fiction.setPlaceholderCount(count); // カウントした数を設定
        
        // 3. データベースに保存
        fictionRepository.save(fiction);
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
