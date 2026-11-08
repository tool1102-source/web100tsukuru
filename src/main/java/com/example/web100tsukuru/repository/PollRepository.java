package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.Poll;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {

    // 受付中（締め切りが近い順）
    List<Poll> findByClosesAtAfterOrderByClosesAtAscIdAsc(LocalDateTime now, Pageable pageable);

    // 結果が出たもの（締め切りが新しい順）
    List<Poll> findByClosesAtLessThanEqualOrderByClosesAtDescIdDesc(LocalDateTime now, Pageable pageable);

    // 管理画面・サイトマップ用（新しい順）
    Page<Poll> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    // マイページ：自分が出したお題
    List<Poll> findByOwnerUserIdOrderByCreatedAtDescIdDesc(Long ownerUserId);

    // 「フォロー中」：フォローしている人が開催した選挙（新しい順）
    List<Poll> findByOwnerUserIdInOrderByCreatedAtDescIdDesc(Collection<Long> ownerUserIds, Pageable pageable);

    // 締め切ったのに、まだ1位以外の券を返していないお題
    @Query("select p.id from Poll p where p.closesAt <= :now and p.settledAt is null order by p.closesAt")
    List<Long> idsDueForSettlement(@Param("now") LocalDateTime now, Pageable pageable);

    // 投票と締め切りの処理が同時に走らないように、お題の行を押さえる
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Poll p where p.id = :id")
    Optional<Poll> lockById(@Param("id") Long id);
}
