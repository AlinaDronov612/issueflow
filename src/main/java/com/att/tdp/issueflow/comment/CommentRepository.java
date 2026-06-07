package com.att.tdp.issueflow.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    boolean existsByAuthorId(Long authorId);

    /**
     * Comments in which the given user is mentioned, newest first, paginated.
     * Id is the tiebreaker so paging stays deterministic when timestamps collide.
     */
    Page<Comment> findByMentionedUsersIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}
