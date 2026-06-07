package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/** Maps {@link User} entities to response DTOs so entities never leak to the API. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }
}
