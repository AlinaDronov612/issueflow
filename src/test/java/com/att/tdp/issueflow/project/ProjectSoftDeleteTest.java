package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectSoftDeleteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private User admin;
    private Project project;
    private RequestPostProcessor asAdmin;
    private RequestPostProcessor asDev;

    @BeforeEach
    void setUp() {
        projectRepository.deleteAll();
        userRepository.deleteAll();
        admin = userRepository.save(User.builder().username("admin2").email("a2@e.com")
                .fullName("Admin Two").role(Role.ADMIN).passwordHash("x").build());
        User dev = userRepository.save(User.builder().username("dev2").email("d2@e.com")
                .fullName("Dev Two").role(Role.DEVELOPER).passwordHash("x").build());
        project = projectRepository.save(Project.builder()
                .name("P").description("d").owner(admin).build());
        asAdmin = asUser(admin);
        asDev = asUser(dev);
    }

    @Test
    void softDeleteHidesProjectFromNormalReadsButKeepsRow() throws Exception {
        mockMvc.perform(delete("/projects/{id}", project.getId()).with(asDev))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{id}", project.getId()).with(asDev))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/projects").with(asDev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Row still exists, just flagged deleted.
        assertThat(projectRepository.findById(project.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void deletedListingIsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/projects/{id}", project.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(get("/projects/deleted").with(asDev))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedListingReturnsRecordsForAdmin() throws Exception {
        mockMvc.perform(delete("/projects/{id}", project.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(get("/projects/deleted").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(project.getId().intValue()));
    }

    @Test
    void restoreIsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/projects/{id}", project.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(post("/projects/{id}/restore", project.getId()).with(asDev))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanRestoreSoftDeletedProject() throws Exception {
        mockMvc.perform(delete("/projects/{id}", project.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(post("/projects/{id}/restore", project.getId()).with(asAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{id}", project.getId()).with(asAdmin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/projects/deleted").with(asAdmin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteUnknownProjectReturns404() throws Exception {
        mockMvc.perform(delete("/projects/{id}", 99999).with(asAdmin))
                .andExpect(status().isNotFound());
    }

    private RequestPostProcessor asUser(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId(), user.getUsername(), user.getRole()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
