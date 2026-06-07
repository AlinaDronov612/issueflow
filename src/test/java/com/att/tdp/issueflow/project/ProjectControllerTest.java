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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser // satisfies authentication for endpoints that don't read the principal
@Transactional // roll back each test for isolation across the shared context
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        projectRepository.deleteAll(); // projects reference users (FK) -> delete first
        userRepository.deleteAll();
        owner = userRepository.save(User.builder()
                .username("owner")
                .email("owner@example.com")
                .fullName("Project Owner")
                .role(Role.DEVELOPER)
                .passwordHash("x")
                .build());
    }

    @Test
    void createProjectReturns200WithOwnerId() throws Exception {
        String body = """
                {"name":"Sample Project","description":"A sample project","ownerId":%d}"""
                .formatted(owner.getId());

        mockMvc.perform(post("/projects").with(asUser(owner))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Sample Project"))
                .andExpect(jsonPath("$.description").value("A sample project"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId().intValue()));
    }

    @Test
    void createProjectDefaultsOwnerToPrincipalWhenOwnerIdOmitted() throws Exception {
        mockMvc.perform(post("/projects").with(asUser(owner))
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"No Owner Field","description":"d"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(owner.getId().intValue()));
    }

    @Test
    void createProjectWithMismatchedOwnerIdReturns403() throws Exception {
        String body = """
                {"name":"Sample","description":"d","ownerId":%d}"""
                .formatted(owner.getId() + 999);

        mockMvc.perform(post("/projects").with(asUser(owner))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("ownerId")));
    }

    @Test
    void createProjectWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/projects").with(asUser(owner))
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"","description":"d"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name")));
    }

    @Test
    void getAllProjectsReturnsList() throws Exception {
        seedProject("Alpha");
        seedProject("Beta");

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getProjectByIdReturns200() throws Exception {
        Project project = seedProject("Alpha");

        mockMvc.perform(get("/projects/{id}", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(project.getId().intValue()))
                .andExpect(jsonPath("$.name").value("Alpha"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId().intValue()));
    }

    @Test
    void getUnknownProjectReturns404() throws Exception {
        mockMvc.perform(get("/projects/{id}", 9999))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateNameAndDescriptionReturns200() throws Exception {
        Project project = seedProject("Alpha");

        mockMvc.perform(patch("/projects/{id}", project.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"Updated Name","description":"Updated description"}"""))
                .andExpect(status().isOk());

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Updated Name");
        assertThat(reloaded.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void partialUpdateLeavesOmittedFieldUnchanged() throws Exception {
        Project project = seedProject("Alpha");

        mockMvc.perform(patch("/projects/{id}", project.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"Renamed"}"""))
                .andExpect(status().isOk());

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getDescription()).isEqualTo("seed description");
    }

    @Test
    void updateBlankNameReturns400() throws Exception {
        Project project = seedProject("Alpha");

        mockMvc.perform(patch("/projects/{id}", project.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name")));
    }

    @Test
    void updateUnknownProjectReturns404() throws Exception {
        mockMvc.perform(patch("/projects/{id}", 9999)
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"X"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/projects").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    private Project seedProject(String name) {
        return projectRepository.save(Project.builder()
                .name(name)
                .description("seed description")
                .owner(owner)
                .build());
    }

    private RequestPostProcessor asUser(User user) {
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
