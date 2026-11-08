package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.FollowService;
import com.example.web100tsukuru.service.FollowService.FollowResult;
import com.example.web100tsukuru.service.PollService;
import com.example.web100tsukuru.service.VoteLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 「ちいさな投票所」の人ごとのページと、フォロー。
 * 人ごとのページに出すのは、表示名・開催した選挙・フォロー数とフォロワー数だけ
 * （ログイン ID や、だれがどの選挙に投票したかは出さない）。
 */
@Controller
public class VoteUserController {

    private final AccountService accounts;
    private final FollowService follows;
    private final PollService polls;
    private final VoteLogin login;

    public VoteUserController(AccountService accounts, FollowService follows, PollService polls, VoteLogin login) {
        this.accounts = accounts;
        this.follows = follows;
        this.polls = polls;
        this.login = login;
    }

    /** 人ごとのページ (GET /vote/users/{id}) */
    @GetMapping("/vote/users/{id:\\d+}")
    public String show(@PathVariable Long id, HttpServletRequest request, Model model) {
        AppUser user = accounts.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long viewerId = login.currentUserId(request);
        model.addAttribute("user", user);
        model.addAttribute("self", user.getId().equals(viewerId));
        model.addAttribute("following", follows.isFollowing(viewerId, id));
        model.addAttribute("followingCount", follows.followingCount(id));
        model.addAttribute("followerCount", follows.followerCount(id));
        model.addAttribute("hosted", polls.ownedBy(id));
        return "vote/user";
    }

    /** フォローする (POST /vote/users/{id}/follow) */
    @PostMapping("/vote/users/{id:\\d+}/follow")
    public String follow(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        Long viewerId = login.currentUserId(request);
        if (viewerId == null) {
            return "redirect:/vote/login?next=/vote/users/" + id;
        }
        FollowResult result = follows.follow(viewerId, id);
        switch (result) {
            case OK -> redirect.addFlashAttribute("notice",
                    accounts.find(id).map(u -> u.getDisplayName() + " さんをフォローしました。").orElse("フォローしました。"));
            case SELF -> redirect.addFlashAttribute("error", "自分をフォローすることはできません。");
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case ALREADY -> { }
        }
        return "redirect:/vote/users/" + id;
    }

    /** フォローを解除する (POST /vote/users/{id}/unfollow) */
    @PostMapping("/vote/users/{id:\\d+}/unfollow")
    public String unfollow(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        Long viewerId = login.currentUserId(request);
        if (viewerId == null) {
            return "redirect:/vote/login?next=/vote/users/" + id;
        }
        follows.unfollow(viewerId, id);
        redirect.addFlashAttribute("notice", "フォローを解除しました。");
        return "redirect:/vote/users/" + id;
    }
}
