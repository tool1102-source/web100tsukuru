package com.example.web100tsukuru.service;

import com.example.web100tsukuru.dto.FictionForm;
import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.entity.FictionText;
import com.example.web100tsukuru.repository.FictionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class FictionService {

    /** 一覧の1ページあたりの件数 */
    public static final int PAGE_SIZE = 20;

    private final FictionRepository fictionRepository;

    public FictionService(FictionRepository fictionRepository) {
        this.fictionRepository = fictionRepository;
    }

    @Transactional
    public Fiction saveFiction(FictionForm form) {
        Fiction fiction = new Fiction();
        fiction.setTitle(form.getTitle().strip());
        fiction.setBody(form.getBody());
        fiction.setAuthorName(form.getAuthorName().strip());
        fiction.setPlaceholderCount(FictionText.countPlaceholders(form.getBody()));
        return fictionRepository.save(fiction);
    }

    /** 投稿一覧（新しい順）。page は 1 始まり */
    public Page<Fiction> findPage(int page) {
        return fictionRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE));
    }

    /** サイトマップ用（新しい順に最大 size 件） */
    public Page<Fiction> findLatest(int size) {
        return fictionRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, size));
    }

    /** 投稿1件 */
    public Optional<Fiction> findById(Long id) {
        return fictionRepository.findById(id);
    }
}
