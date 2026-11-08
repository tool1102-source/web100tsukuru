package com.example.web100tsukuru.service;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.Poll;
import com.example.web100tsukuru.entity.PollVote;
import com.example.web100tsukuru.entity.TicketLedger;
import com.example.web100tsukuru.entity.TicketLedger.Reason;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.PollRepositories.OptionTally;
import com.example.web100tsukuru.repository.PollRepository;
import com.example.web100tsukuru.repository.PollVoteRepository;
import com.example.web100tsukuru.repository.TicketLedgerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 締め切ったお題の後かたづけ：1位にならなかった選択肢に入れた券を、入れた人に返す（引き分けなら全部返す）。
 *
 * 決まった時刻に動かす仕組みは使わない（DB を定期的に起こすと、無料の Neon の稼働時間を使い切るため）。
 * 代わりに、だれかが投票所のページを開いたときに、締め切りを過ぎたお題をまとめて片づける。
 * 同じお題を二度片づけないよう、お題の行を押さえてから settledAt を見る。
 */
@Component
public class PollSettlement {

    private static final int BATCH = 100;

    private final PollRepository polls;
    private final PollVoteRepository votes;
    private final AppUserRepository users;
    private final TicketLedgerRepository ledger;

    public PollSettlement(PollRepository polls, PollVoteRepository votes, AppUserRepository users, TicketLedgerRepository ledger) {
        this.polls = polls;
        this.votes = votes;
        this.users = users;
        this.ledger = ledger;
    }

    /** 締め切りを過ぎて、まだ片づけていないお題を片づける */
    @Transactional
    public void settleAllDue() {
        for (Long id : polls.idsDueForSettlement(LocalDateTime.now(Web100tsukuruApplication.ZONE), PageRequest.of(0, BATCH))) {
            settle(id);
        }
    }

    /**
     * 締め切りの結果。
     * winner … 1位の選択肢（だれも入れていない・引き分けなら null）
     * draw … 引き分け（券の枚数も入れた人数も同じで並んだ）。このときは券を全部返す
     * byVoters … 券の枚数が同じで、入れた人数で1位が決まった
     */
    public record Outcome(Long winner, boolean draw, boolean byVoters) {

        public static final Outcome NO_VOTES = new Outcome(null, false, false);
    }

    /** 1件を片づける。片づけたら true（受付中・片づけずみなら false） */
    @Transactional
    public boolean settle(Long pollId) {
        Poll poll = polls.lockById(pollId).orElse(null);
        if (poll == null || poll.isOpen() || poll.getSettledAt() != null) {
            return false;
        }
        Outcome outcome = outcome(pollId);
        List<PollVote> notRefunded = votes.findByPollIdAndRefundedFalse(pollId);
        if (outcome.draw()) {
            refund(notRefunded, Reason.DRAW);
        } else {
            refund(notRefunded.stream().filter(v -> !v.getOptionId().equals(outcome.winner())).toList(), Reason.REFUND);
        }
        poll.markSettled();
        return true;
    }

    public Outcome outcome(Long pollId) {
        return decide(votes.talliesByOptionIn(List.of(pollId)));
    }

    /**
     * 1位を決める（1つのお題の選択肢ごとの集計から）。
     * 券がいちばん多い選択肢が1位。券の枚数が同じなら、入れた人数が多いほうが1位。
     * 人数も同じなら引き分け。だれも入れていなければ NO_VOTES
     */
    public static Outcome decide(List<OptionTally> tallies) {
        long maxTickets = tallies.stream().mapToLong(OptionTally::getTickets).max().orElse(0);
        if (maxTickets == 0) {
            return Outcome.NO_VOTES;
        }
        List<OptionTally> top = tallies.stream().filter(t -> t.getTickets() == maxTickets).toList();
        if (top.size() == 1) {
            return new Outcome(top.get(0).getOptionId(), false, false);
        }
        long maxVoters = top.stream().mapToLong(OptionTally::getVoters).max().orElse(0);
        List<OptionTally> topByVoters = top.stream().filter(t -> t.getVoters() == maxVoters).toList();
        if (topByVoters.size() == 1) {
            return new Outcome(topByVoters.get(0).getOptionId(), false, true);
        }
        return new Outcome(null, true, false);
    }

    /**
     * 票の券を、入れた人に同じ種類（無料・有料）で返す。
     * 何人分もの行を押さえるので、別の片づけと押さえ合いにならないよう、人の番号の順に押さえる。
     */
    @Transactional
    public void refund(List<PollVote> toRefund, Reason reason) {
        List<PollVote> ordered = toRefund.stream().sorted(Comparator.comparing(PollVote::getUserId)).toList();
        for (PollVote vote : ordered) {
            AppUser user = users.lockById(vote.getUserId()).orElse(null);
            if (user != null) {
                user.addTickets(vote.getFreeUsed(), vote.getPaidUsed());
                ledger.save(new TicketLedger(user.getId(), vote.getFreeUsed(), vote.getPaidUsed(), reason, vote.getPollId()));
            }
            vote.markRefunded();
        }
    }
}
