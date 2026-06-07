package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // roll back each test for isolation across the shared context
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username("jdoe")
                .email("jdoe@example.com")
                .fullName("John Doe")
                .role(Role.DEVELOPER)
                .passwordHash(passwordEncoder.encode("secret123"))
                .build());
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content("""
                        {"username":"jdoe","password":"secret123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content("""
                        {"username":"jdoe","password":"wrong"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Invalid username or password")));
    }

    @Test
    void loginWithUnknownUserReturns401() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content("""
                        {"username":"ghost","password":"secret123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithBlankPasswordReturns400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content("""
                        {"username":"jdoe","password":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void meWithTokenReturnsCurrentUserShape() throws Exception {
        String token = loginAndGetToken("jdoe", "secret123");

        mockMvc.perform(get("/auth/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.email").value("jdoe@example.com"))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithTokenSucceeds() throws Exception {
        String token = loginAndGetToken("jdoe", "secret123");

        mockMvc.perform(get("/users").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/auth/me").header(AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTokenSoItCannotBeReused() throws Exception {
        String token = loginAndGetToken("jdoe", "secret123");

        // Token works before logout.
        mockMvc.perform(get("/auth/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Same token is now revoked.
        mockMvc.perform(get("/auth/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginResponseDoesNotLeakPasswordHash() throws Exception {
        String response = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content("""
                        {"username":"jdoe","password":"secret123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(containsString("secret123"))))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain("passwordHash");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asText();
    }
}
