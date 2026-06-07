package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.auth.dto.LoginResponse;
import com.att.tdp.issueflow.common.exception.UnauthorizedException;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserMapper;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenDenyList tokenDenyList;
    private final UserMapper userMapper;

    /** Validates credentials and returns a signed JWT. Same error for unknown user or bad password. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for username '{}' (unknown user)", request.username());
                    return new UnauthorizedException("Invalid username or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for username '{}' (bad password)", request.username());
            throw new UnauthorizedException("Invalid username or password");
        }

        String token = jwtService.generateToken(user);
        log.info("User '{}' (id {}) logged in", user.getUsername(), user.getId());
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }

    /** Resolves the current user from the authenticated principal. */
    @Transactional(readOnly = true)
    public UserResponse me(AuthPrincipal principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
        return userMapper.toResponse(user);
    }

    /** Revokes the current token by adding its jti to the deny-list. */
    public void logout(String tokenId) {
        if (tokenId != null) {
            tokenDenyList.revoke(tokenId);
        }
    }
}
