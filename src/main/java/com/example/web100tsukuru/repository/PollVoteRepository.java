package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.PollVote;
import com.example.web100tsukuru.repository.PollRepositories.OptionTally;
import com.example.web100tsukuru.repository.PollRepositories.OptionTotal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    // 選択肢ごとの券の合計と入れた人数を、いくつかのお題についてまとめて
    // （返した券も含む。結果はこの2つで決まる：券が多いほう、同じなら人数が多いほう）
    @Query("select v.pollId as pollId, v.optionId as optionId, sum(v.tickets) as tickets, count(distinct v.userId) as voters"
            + " from PollVote v where v.pollId in :pollIds group by v.pollId, v.optionId")
    List<OptionTally> talliesByOptionIn(@Param("pollIds") Collection<Long> pollIds);

    // ある人が選択肢ごとに入れた券の合計
    @Query("select v.optionId as optionId, sum(v.tickets) as tickets from PollVote v where v.pollId = :pollId and v.userId = :userId group by v.optionId")
    List<OptionTotal> totalsByOptionForUser(@Param("pollId") Long pollId, @Param("userId") Long userId);

    // まだ返していない票（締め切りやお題を消すときに返す）
    List<PollVote> findByPollIdAndRefundedFalse(Long pollId);

    // ある人が券を入れたお題
    @Query("select distinct v.pollId from PollVote v where v.userId = :userId")
    List<Long> pollIdsVotedBy(@Param("userId") Long userId);

    @Modifying
    @Query("delete from PollVote v where v.pollId in :pollIds")
    int deleteByPollIds(@Param("pollIds") Collection<Long> pollIds);
}
