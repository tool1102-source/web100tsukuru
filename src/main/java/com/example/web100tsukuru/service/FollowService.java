package com.example.web100tsukuru.service;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.UserFollow;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.UserFollowRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 「ちいさな投票所」のフォロー。
 * だれをフォローしているかは本人のマイページにだけ出し、ほかの人には人数だけを見せる。
 */
@Service
public class FollowService {

    public enum FollowResult { OK, ALREADY, SELF, NOT_FOUND }

    private final UserFollowRepository follows;
    private final AppUserRepository users;

    public FollowService(UserFollowRepository follows, AppUserRepository users) {
        this.follows = follows;
        this.users = users;
    }

    /**
     * フォローする。自分自身・いない人はフォローできない。
     * 二度押しで同時に届いても、組み合わせの重複は DB の決まりで止まるので ALREADY にする。
     */
    public FollowResult follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            return FollowResult.SELF;
        }
        if (!users.existsById(followeeId)) {
            return FollowResult.NOT_FOUND;
        }
        if (follows.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            return FollowResult.ALREADY;
        }
        try {
            follows.saveAndFlush(new UserFollow(followerId, followeeId));
        } catch (DataIntegrityViolationException e) {
            return FollowResult.ALREADY;
        }
        return FollowResult.OK;
    }

    /** フォローを解除する。していなければ何もしない */
    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        follows.deletePair(followerId, followeeId);
    }

    public boolean isFollowing(Long followerId, Long followeeId) {
        return followerId != null && follows.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    public long followingCount(Long userId) {
        return follows.countByFollowerId(userId);
    }

    public long followerCount(Long userId) {
        return follows.countByFolloweeId(userId);
    }

    /** フォローしている人の番号（新しくフォローした順） */
    public List<Long> followeeIds(Long followerId) {
        return follows.findByFollowerIdOrderByCreatedAtDescIdDesc(followerId).stream()
                .map(UserFollow::getFolloweeId)
                .toList();
    }

    /** フォローしている人（マイページの一覧用。新しくフォローした順） */
    public List<AppUser> followees(Long followerId) {
        List<Long> ids = followeeIds(followerId);
        List<AppUser> found = users.findAllById(ids);
        return ids.stream()
                .flatMap(id -> found.stream().filter(u -> u.getId().equals(id)))
                .toList();
    }
}
