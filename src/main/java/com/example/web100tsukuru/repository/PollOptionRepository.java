package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

    List<PollOption> findByPollIdOrderByOptionNo(Long pollId);

    List<PollOption> findByPollIdInOrderByPollIdAscOptionNoAsc(Collection<Long> pollIds);

    @Modifying
    @Query("delete from PollOption o where o.pollId in :pollIds")
    int deleteByPollIds(@Param("pollIds") Collection<Long> pollIds);
}
