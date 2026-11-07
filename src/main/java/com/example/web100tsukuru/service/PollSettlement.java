package com.example.web100tsukuru.service;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.Poll;
import com.example.web100tsukuru.entity.PollVote;
import com.example.web100tsukuru.entity.TicketLedger;
import com.example.web100tsukuru.entity.TicketLedger.Reason;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.PollRepositories.OptionTotal;
import com.example.web100tsukuru.repository.PollRepository;
import com.example.web100tsukuru.repository.PollVoteRepository;
import com.example.web100tsukuru.repository.TicketLedgerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 締め切ったお題の後かたづけ：1位にならなかった選択肢に入れた券を、入れた人に返す。
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

    /** 1件を片づける。片づけたら true（受付中・片づけずみなら false） */
    @Transactional
    public boolean settle(Long pollId) {
        Poll poll = polls.lockById(pollId).orElse(null);
        if (poll == null || poll.isOpen() || poll.getSettledAt() != null) {
            return false;
        }
        Set<Long> winners = winners(pollId);
        refund(votes.findByPollIdAndRefundedFalse(pollId).stream()
                .filter(v -> !winners.contains(v.getOptionId()))
                .toList(), Reason.REFUND);
        poll.markSettled();
        return true;
    }

    /** 券がいちばん多い選択肢（同じ数で並んだら全部が1位）。だれも入れていなければ空 */
    public Set<Long> winners(Long pollId) {
        List<OptionTotal> totals = votes.totalsByOption(pollId);
        long max = totals.stream().mapToLong(OptionTotal::getTickets).max().orElse(0);
        if (max == 0) {
            return Set.of();
        }
        return totals.stream().filter(t -> t.getTickets() == max).map(OptionTotal::getOptionId).collect(Collectors.toSet());
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
