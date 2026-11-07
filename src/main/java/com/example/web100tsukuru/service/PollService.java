package com.example.web100tsukuru.service;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.Poll;
import com.example.web100tsukuru.entity.PollImage;
import com.example.web100tsukuru.entity.PollOption;
import com.example.web100tsukuru.entity.PollVote;
import com.example.web100tsukuru.entity.TicketLedger;
import com.example.web100tsukuru.entity.TicketLedger.Reason;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.PollImageRepository;
import com.example.web100tsukuru.repository.PollOptionRepository;
import com.example.web100tsukuru.repository.PollRepositories.OptionTotal;
import com.example.web100tsukuru.repository.PollRepositories.PollImageInfo;
import com.example.web100tsukuru.repository.PollRepositories.PollTotal;
import com.example.web100tsukuru.repository.PollRepository;
import com.example.web100tsukuru.repository.PollVoteRepository;
import com.example.web100tsukuru.repository.TicketLedgerRepository;
import com.example.web100tsukuru.service.ImageProcessor.ProcessedImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 「ちいさな投票所」のお題・投票・報告 */
@Service
@Transactional(readOnly = true)
public class PollService {

    public static final int MAX_REPORT_IMAGES = 3;
    public static final long MAX_TICKETS_PER_VOTE = 1_000_000;

    /** 締め切りまでの長さ（Xの投票と同じくらいの幅） */
    public enum PollDuration {
        HOUR_1("1時間", Duration.ofHours(1)),
        HOUR_6("6時間", Duration.ofHours(6)),
        DAY_1("1日", Duration.ofDays(1)),
        DAY_3("3日", Duration.ofDays(3));

        private final String label;
        private final Duration length;

        PollDuration(String label, Duration length) {
            this.label = label;
            this.length = length;
        }

        public String getLabel() {
            return label;
        }

        public Duration getLength() {
            return length;
        }
    }

    /** 選択肢1つの表示用 */
    public record OptionView(Long id, int optionNo, String label, long tickets, int percent, long myTickets, boolean winner) {
    }

    /** お題のページの表示用 */
    public record PollView(Poll poll, String ownerName, List<OptionView> options, long totalTickets, long myTotal,
                           PollImageInfo questionImage, List<PollImageInfo> reportImages, boolean owner) {

        public List<OptionView> winners() {
            return options.stream().filter(OptionView::winner).toList();
        }

        public String ownerInitial() {
            return Avatars.initial(ownerName);
        }

        public int ownerColor() {
            return Avatars.color(ownerName);
        }
    }

    /** 一覧の1件 */
    public record PollSummary(Poll poll, String ownerName, List<String> optionLabels, long totalTickets,
                              List<String> winnerLabels, boolean hasImage) {

        public String ownerInitial() {
            return Avatars.initial(ownerName);
        }

        public int ownerColor() {
            return Avatars.color(ownerName);
        }

        /** 画像のないお題のカードに出す色の番号（お題ごとに変える） */
        public int thumbColor() {
            return (int) Math.floorMod(poll.getId(), (long) Avatars.COLORS);
        }
    }

    public enum VoteResult { OK, NOT_FOUND, CLOSED, INVALID, NOT_ENOUGH }

    public enum ReportResult { OK, NOT_FOUND, NOT_OWNER, NOT_CLOSED, ALREADY }

    private final PollRepository polls;
    private final PollOptionRepository options;
    private final PollVoteRepository votes;
    private final PollImageRepository images;
    private final AppUserRepository users;
    private final TicketLedgerRepository ledger;
    private final PollSettlement settlement;

    public PollService(PollRepository polls, PollOptionRepository options, PollVoteRepository votes,
                       PollImageRepository images, AppUserRepository users, TicketLedgerRepository ledger,
                       PollSettlement settlement) {
        this.polls = polls;
        this.options = options;
        this.votes = votes;
        this.images = images;
        this.users = users;
        this.ledger = ledger;
        this.settlement = settlement;
    }

    // ==========================================================
    // 出す・入れる・報告する
    // ==========================================================

    /** お題を出す。選択肢は2〜4個（中身の確かめは呼ぶ側） */
    @Transactional
    public Poll create(Long ownerUserId, String title, String detail, List<String> labels, PollDuration duration,
                       ProcessedImage image) {
        Poll poll = new Poll();
        poll.setOwnerUserId(ownerUserId);
        poll.setTitle(title.strip());
        poll.setDetail(detail == null || detail.isBlank() ? null : detail.strip());
        poll.setClosesAt(LocalDateTime.now(Web100tsukuruApplication.ZONE).plus(duration.getLength()));
        polls.save(poll);
        for (int i = 0; i < labels.size(); i++) {
            options.save(new PollOption(poll.getId(), i + 1, labels.get(i).strip()));
        }
        if (image != null) {
            images.save(new PollImage(poll.getId(), PollImage.QUESTION_SLOT, image.width(), image.height(), image.jpeg()));
        }
        return poll;
    }

    /** 投票券を入れる。無料の券から先に使う */
    @Transactional
    public VoteResult vote(Long pollId, Long optionId, Long userId, long tickets) {
        if (tickets < 1 || tickets > MAX_TICKETS_PER_VOTE) {
            return VoteResult.INVALID;
        }
        Poll poll = polls.lockById(pollId).orElse(null);
        if (poll == null) {
            return VoteResult.NOT_FOUND;
        }
        if (!poll.isOpen()) {
            return VoteResult.CLOSED;
        }
        boolean validOption = options.findById(optionId).map(o -> o.getPollId().equals(pollId)).orElse(false);
        if (!validOption) {
            return VoteResult.INVALID;
        }
        AppUser user = users.lockById(userId).orElse(null);
        if (user == null || user.getTotalTickets() < tickets) {
            return VoteResult.NOT_ENOUGH;
        }
        long free = Math.min(tickets, user.getFreeTickets());
        long paid = tickets - free;
        user.addTickets(-free, -paid);
        votes.save(new PollVote(pollId, optionId, userId, free, paid));
        ledger.save(new TicketLedger(userId, -free, -paid, Reason.VOTE, pollId));
        return VoteResult.OK;
    }

    /** 1位のとおりにやったことを報告する（出した人だけ・締め切りのあと・1回だけ） */
    @Transactional
    public ReportResult report(Long pollId, Long userId, String text, List<ProcessedImage> reportImages) {
        Poll poll = polls.lockById(pollId).orElse(null);
        if (poll == null) {
            return ReportResult.NOT_FOUND;
        }
        if (!poll.getOwnerUserId().equals(userId)) {
            return ReportResult.NOT_OWNER;
        }
        if (poll.isOpen()) {
            return ReportResult.NOT_CLOSED;
        }
        if (poll.isReported()) {
            return ReportResult.ALREADY;
        }
        poll.report(text.strip());
        for (int i = 0; i < reportImages.size(); i++) {
            ProcessedImage image = reportImages.get(i);
            images.save(new PollImage(pollId, i + 1, image.width(), image.height(), image.jpeg()));
        }
        return ReportResult.OK;
    }

    // ==========================================================
    // 見る
    // ==========================================================

    public Optional<Poll> findById(Long pollId) {
        return polls.findById(pollId);
    }

    /** お題のページ。viewerUserId はログインしていなければ null */
    public Optional<PollView> view(Long pollId, Long viewerUserId) {
        Poll poll = polls.findById(pollId).orElse(null);
        if (poll == null) {
            return Optional.empty();
        }
        Map<Long, Long> totals = toMap(votes.totalsByOption(pollId));
        Map<Long, Long> mine = viewerUserId == null ? Map.of() : toMap(votes.totalsByOptionForUser(pollId, viewerUserId));
        long total = totals.values().stream().mapToLong(Long::longValue).sum();
        Set<Long> winners = poll.isOpen() ? Set.of() : settlement.winners(pollId);
        List<OptionView> optionViews = options.findByPollIdOrderByOptionNo(pollId).stream()
                .map(o -> {
                    long t = totals.getOrDefault(o.getId(), 0L);
                    int percent = total == 0 ? 0 : (int) Math.round(t * 100.0 / total);
                    return new OptionView(o.getId(), o.getOptionNo(), o.getLabel(), t, percent,
                            mine.getOrDefault(o.getId(), 0L), winners.contains(o.getId()));
                })
                .toList();
        List<PollImageInfo> allImages = images.findByPollIdOrderBySlot(pollId);
        PollImageInfo question = allImages.stream().filter(i -> i.getSlot() == PollImage.QUESTION_SLOT).findFirst().orElse(null);
        List<PollImageInfo> report = allImages.stream().filter(i -> i.getSlot() != PollImage.QUESTION_SLOT).toList();
        String ownerName = users.findById(poll.getOwnerUserId()).map(AppUser::getDisplayName).orElse("退会した人");
        long myTotal = mine.values().stream().mapToLong(Long::longValue).sum();
        return Optional.of(new PollView(poll, ownerName, optionViews, total, myTotal, question, report,
                poll.getOwnerUserId().equals(viewerUserId)));
    }

    public List<PollSummary> openPolls(int limit) {
        return summaries(polls.findByClosesAtAfterOrderByClosesAtAscIdAsc(now(), PageRequest.of(0, limit)));
    }

    public List<PollSummary> closedPolls(int limit) {
        return summaries(polls.findByClosesAtLessThanEqualOrderByClosesAtDescIdDesc(now(), PageRequest.of(0, limit)));
    }

    /** マイページ：自分が出したお題 */
    public List<PollSummary> ownedBy(Long userId) {
        return summaries(polls.findByOwnerUserIdOrderByCreatedAtDescIdDesc(userId));
    }

    /** マイページ：自分が券を入れたお題 */
    public List<PollSummary> votedBy(Long userId) {
        List<Poll> voted = polls.findAllById(votes.pollIdsVotedBy(userId)).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        return summaries(voted);
    }

    /** 管理画面・サイトマップ用（新しい順） */
    public Page<Poll> page(int page, int size) {
        return polls.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(page, 1) - 1, size));
    }

    public List<PollSummary> summaries(List<Poll> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Poll::getId).toList();
        Map<Long, List<PollOption>> optionsByPoll = options.findByPollIdInOrderByPollIdAscOptionNoAsc(ids).stream()
                .collect(Collectors.groupingBy(PollOption::getPollId));
        Map<Long, Long> totalByOption = toMap(votes.totalsByOptionIn(ids));
        Map<Long, Long> totalByPoll = votes.totalsByPoll(ids).stream()
                .collect(Collectors.toMap(PollTotal::getPollId, PollTotal::getTickets));
        Set<Long> withImage = images.findByPollIdInAndSlot(ids, PollImage.QUESTION_SLOT).stream()
                .map(PollImageInfo::getPollId).collect(Collectors.toSet());
        Map<Long, String> names = ownerNames(list.stream().map(Poll::getOwnerUserId).toList());

        List<PollSummary> result = new ArrayList<>();
        for (Poll poll : list) {
            List<PollOption> opts = optionsByPoll.getOrDefault(poll.getId(), List.of());
            List<String> winnerLabels = List.of();
            if (!poll.isOpen()) {
                long max = opts.stream().mapToLong(o -> totalByOption.getOrDefault(o.getId(), 0L)).max().orElse(0);
                if (max > 0) {
                    winnerLabels = opts.stream().filter(o -> totalByOption.getOrDefault(o.getId(), 0L) == max)
                            .map(PollOption::getLabel).toList();
                }
            }
            result.add(new PollSummary(poll, names.getOrDefault(poll.getOwnerUserId(), "退会した人"),
                    opts.stream().map(PollOption::getLabel).toList(), totalByPoll.getOrDefault(poll.getId(), 0L),
                    winnerLabels, withImage.contains(poll.getId())));
        }
        return result;
    }

    public Optional<PollImage> findImage(Long pollId, int slot) {
        return images.findByPollIdAndSlot(pollId, slot);
    }

    // ==========================================================
    // 管理画面
    // ==========================================================

    /** お題をまとめて消す。まだ返していない券は、全部返してから消す。消したお題を返す */
    @Transactional
    public List<Poll> deleteAll(Collection<Long> ids) {
        List<Poll> deleted = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            Poll poll = polls.lockById(id).orElse(null);
            if (poll == null) {
                continue;
            }
            settlement.refund(votes.findByPollIdAndRefundedFalse(id), Reason.POLL_DELETED);
            images.deleteByPollIds(List.of(id));
            votes.deleteByPollIds(List.of(id));
            options.deleteByPollIds(List.of(id));
            polls.delete(poll);
            deleted.add(poll);
        }
        return deleted;
    }

    private Map<Long, String> ownerNames(Collection<Long> userIds) {
        return users.findAllById(new LinkedHashSet<>(userIds)).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }

    private static Map<Long, Long> toMap(List<OptionTotal> totals) {
        return totals.stream().collect(Collectors.toMap(OptionTotal::getOptionId, OptionTotal::getTickets));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(Web100tsukuruApplication.ZONE);
    }
}
