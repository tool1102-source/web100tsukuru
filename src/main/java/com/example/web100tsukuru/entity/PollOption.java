package com.example.web100tsukuru.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** お題の選択肢（2〜4個） */
@Entity
@Table(indexes = @Index(name = "poll_option_poll", columnList = "pollId, optionNo"))
public class PollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pollId;

    // 並び順（1 始まり）
    @Column(nullable = false)
    private int optionNo;

    @Column(nullable = false, length = 100)
    private String label;

    public PollOption() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public PollOption(Long pollId, int optionNo, String label) {
        this.pollId = pollId;
        this.optionNo = optionNo;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public Long getPollId() {
        return pollId;
    }

    public int getOptionNo() {
        return optionNo;
    }

    public String getLabel() {
        return label;
    }
}
