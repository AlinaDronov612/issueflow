package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser // user endpoints are now JWT-protected; authenticate these tests
@Transactional // roll back each test for isolation across the shared context
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    private static final String VALID_USER = """
            {"username":"jdoe","email":"jdoe@example.com","fullName":"John Doe",
             "role":"DEVELOPER","password":"secret123"}""";

    @Test
    void createsUserReturns200WithoutExposingPassword() throws Exception {
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.email").value("jdoe@example.com"))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createdPasswordIsHashedNotStoredInPlaintext() throws Exception {
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isOk());

        User saved = userRepository.findAll().get(0);
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsInvalidEmailWith400() throws Exception {
        String body = """
                {"username":"jdoe","email":"not-an-email","fullName":"John Doe",
                 "role":"DEVELOPER","password":"secret123"}""";
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("email")));
    }

    @Test
    void rejectsBlankUsernameWith400() throws Exception {
        String body = """
                {"username":"","email":"jdoe@example.com","fullName":"John Doe",
                 "role":"DEVELOPER","password":"secret123"}""";
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("username")));
    }

    @Test
    void rejectsShortPasswordWith400() throws Exception {
        String body = """
                {"username":"jdoe","email":"jdoe@example.com","fullName":"John Doe",
                 "role":"DEVELOPER","password":"123"}""";
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("password")));
    }

    @Test
    void rejectsInvalidRoleEnumWithInformative400() throws Exception {
        String body = """
                {"username":"jdoe","email":"jdoe@example.com","fullName":"John Doe",
                 "role":"MANAGER","password":"secret123"}""";
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("role must be one of")));
    }

    @Test
    void rejectsDuplicateUsernameWith409() throws Exception {
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isOk());

        String duplicate = """
                {"username":"jdoe","email":"other@example.com","fullName":"Other",
                 "role":"DEVELOPER","password":"secret123"}""";
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(duplicate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already taken")));
    }

    @Test
    void rejectsDuplicateEmailWith409() throws Exception {
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isOk());

        String duplicate = """
                {"username":"other","email":"jdoe@example.com","fullName":"Other",
                 "role":"DEVELOPER","password":"secret123"}""";
        mockMvc.perform(post("/users").contentType(APPLICATION_JSON).content(duplicate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    void getAllUsersReturnsList() throws Exception {
        seedUser("jdoe", "jdoe@example.com", Role.DEVELOPER);
        seedUser("asmith", "asmith@example.com", Role.ADMIN);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getUserByIdReturnsUser() throws Exception {
        User saved = seedUser("jdoe", "jdoe@example.com", Role.DEVELOPER);

        mockMvc.perform(get("/users/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.username").value("jdoe"));
    }

    @Test
    void getUnknownUserReturns404() throws Exception {
        mockMvc.perform(get("/users/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updatesFullNameAndRoleReturns200() throws Exception {
        User saved = seedUser("jdoe", "jdoe@example.com", Role.DEVELOPER);

        String body = """
                {"fullName":"Jane Doe","role":"ADMIN"}""";
        mockMvc.perform(post("/users/update/{id}", saved.getId())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Jane Doe");
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void partialUpdateLeavesOmittedFieldsUnchanged() throws Exception {
        User saved = seedUser("jdoe", "jdoe@example.com", Role.DEVELOPER);

        mockMvc.perform(post("/users/update/{id}", saved.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("John Doe");
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void updateBlankFullNameReturns400() throws Exception {
        User saved = seedUser("jdoe", "jdoe@example.com", Role.DEVELOPER);

        mockMvc.perform(post("/users/update/{id}", saved.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"fullName":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("fullName")));
    }

    @Test
    void updateUnknownUserReturns404() throws Exception {
        mockMvc.perform(post("/users/update/{id}", 9999)
                        .contentType(APPLICATION_JSON).content("""
                                {"fullName":"Jane Doe"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesUserReturns200() throws Exception {
        User saved = seedUser("jdoe", "jdoe@example.com", Role.DEVELOPER);

        mockMvc.perform(delete("/users/{id}", saved.getId()))
                .andExpect(status().isOk());

        assertThat(userRepository.existsById(saved.getId())).isFalse();
    }

    @Test
    void deleteUnknownUserReturns404() throws Exception {
        mockMvc.perform(delete("/users/{id}", 9999))
                .andExpect(status().isNotFound());
    }

    private User seedUser(String username, String email, Role role) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .fullName("John Doe")
                .role(role)
                .passwordHash(passwordEncoder.encode("secret123"))
                .build());
    }
}
