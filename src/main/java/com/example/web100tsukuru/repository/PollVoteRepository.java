package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.PollVote;
import com.example.web100tsukuru.repository.PollRepositories.OptionTotal;
import com.example.web100tsukuru.repository.PollRepositories.PollTotal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    // 選択肢ごとの券の合計（返した券も含む。結果はこの合計で決まる）
    @Query("select v.optionId as optionId, sum(v.tickets) as tickets from PollVote v where v.pollId = :pollId group by v.optionId")
    List<OptionTotal> totalsByOption(@Param("pollId") Long pollId);

    // ある人が選択肢ごとに入れた券の合計
    @Query("select v.optionId as optionId, sum(v.tickets) as tickets from PollVote v where v.pollId = :pollId and v.userId = :userId group by v.optionId")
    List<OptionTotal> totalsByOptionForUser(@Param("pollId") Long pollId, @Param("userId") Long userId);

    // 選択肢ごとの券の合計を、いくつかのお題についてまとめて（一覧用）
    @Query("select v.optionId as optionId, sum(v.tickets) as tickets from PollVote v where v.pollId in :pollIds group by v.optionId")
    List<OptionTotal> totalsByOptionIn(@Param("pollIds") Collection<Long> pollIds);

    // お題ごとの券の合計（一覧用）
    @Query("select v.pollId as pollId, sum(v.tickets) as tickets from PollVote v where v.pollId in :pollIds group by v.pollId")
    List<PollTotal> totalsByPoll(@Param("pollIds") Collection<Long> pollIds);

    // まだ返していない票（締め切りやお題を消すときに返す）
    List<PollVote> findByPollIdAndRefundedFalse(Long pollId);

    // ある人が券を入れたお題
    @Query("select distinct v.pollId from PollVote v where v.userId = :userId")
    List<Long> pollIdsVotedBy(@Param("userId") Long userId);

    @Modifying
    @Query("delete from PollVote v where v.pollId in :pollIds")
    int deleteByPollIds(@Param("pollIds") Collection<Long> pollIds);
}
