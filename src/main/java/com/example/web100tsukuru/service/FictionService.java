package com.example.web100tsukuru.service;

import com.example.web100tsukuru.dto.ArticleBlock;
import com.example.web100tsukuru.dto.FictionForm;
import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.entity.FictionImage;
import com.example.web100tsukuru.entity.FictionText;
import com.example.web100tsukuru.repository.FictionImageInfo;
import com.example.web100tsukuru.repository.FictionImageRepository;
import com.example.web100tsukuru.repository.FictionRepository;
import com.example.web100tsukuru.service.ImageProcessor.ProcessedImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class FictionService {

    /** 一覧の1ページあたりの件数 */
    public static final int PAGE_SIZE = 20;
    /** 1件の投稿に付けられる画像の枚数 */
    public static final int MAX_IMAGES = 5;

    private final FictionRepository fictionRepository;
    private final FictionImageRepository imageRepository;

    public FictionService(FictionRepository fictionRepository, FictionImageRepository imageRepository) {
        this.fictionRepository = fictionRepository;
        this.imageRepository = imageRepository;
    }

    @Transactional
    public Fiction saveFiction(FictionForm form) {
        return saveFiction(form, List.of());
    }

    /** 投稿と、その画像（縮めたもの）を保存する。i 枚目の画像は本文の i 番目の [IMG] に入る */
    @Transactional
    public Fiction saveFiction(FictionForm form, List<ProcessedImage> images) {
        Fiction fiction = new Fiction();
        fiction.setTitle(form.getTitle().strip());
        fiction.setBody(form.getBody());
        fiction.setAuthorName(form.getAuthorName().strip());
        fiction.setPlaceholderCount(FictionText.countPlaceholders(form.getBody()));
        Fiction saved = fictionRepository.save(fiction);
        for (int i = 0; i < images.size(); i++) {
            ProcessedImage image = images.get(i);
            imageRepository.save(new FictionImage(saved.getId(), i + 1, image.width(), image.height(), image.jpeg()));
        }
        return saved;
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

    /** 記事の本文を、文章と画像を上から並べた形にする（画像は [IMG] の位置に入る） */
    public List<ArticleBlock> articleBlocks(Fiction fiction) {
        List<FictionImageInfo> images = imageRepository.findByFictionIdOrderByImageNo(fiction.getId());
        List<String> texts = FictionText.splitForImages(fiction.getBody(), images.size());
        List<ArticleBlock> blocks = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (!texts.get(i).isEmpty()) {
                blocks.add(ArticleBlock.text(texts.get(i)));
            }
            if (i < images.size()) {
                blocks.add(ArticleBlock.image(images.get(i)));
            }
        }
        return blocks;
    }

    /** 画像1枚の中身 */
    public Optional<FictionImage> findImage(Long fictionId, int imageNo) {
        return imageRepository.findByFictionIdAndImageNo(fictionId, imageNo);
    }

    /** 投稿ごとの画像の番号（管理画面の一覧用） */
    public Map<Long, List<Integer>> imageNumbers(Collection<Long> fictionIds) {
        Map<Long, List<Integer>> numbers = new LinkedHashMap<>();
        if (fictionIds.isEmpty()) {
            return numbers;
        }
        for (FictionImageInfo info : imageRepository.findByFictionIdInOrderByFictionIdAscImageNoAsc(fictionIds)) {
            numbers.computeIfAbsent(info.getFictionId(), id -> new ArrayList<>()).add(info.getImageNo());
        }
        return numbers;
    }

    /** 投稿をまとめて消す（管理画面用）。その画像も消す。実際に消した投稿を返す（ない番号は無視） */
    @Transactional
    public List<Fiction> deleteAll(List<Long> ids) {
        List<Fiction> found = fictionRepository.findAllById(ids);
        if (!found.isEmpty()) {
            imageRepository.deleteByFictionIds(found.stream().map(Fiction::getId).toList());
        }
        fictionRepository.deleteAll(found);
        return found;
    }
}
