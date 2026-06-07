package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.common.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    /** Resolve users by username, case-insensitively (for @mention matching). */
    @Query("select u from User u where lower(u.username) in :usernames")
    List<User> findByUsernameLowerIn(@Param("usernames") Collection<String> usernames);

    /**
     * Users of a role, oldest registration first. The {@code id} tiebreaker keeps
     * ordering deterministic when registration timestamps collide — this is the
     * auto-assignment tie-break ("oldest registrant wins").
     */
    List<User> findByRoleOrderByCreatedAtAscIdAsc(Role role);
}
