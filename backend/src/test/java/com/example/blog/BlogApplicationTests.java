package com.example.blog;

import com.example.blog.auth.service.UserService;
import com.example.blog.comment.repository.CommentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlogApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private CommentRepository commentRepository;

    @BeforeEach
    void createAdministrator() {
        userService.createIfAbsent("editor", "correct horse battery staple", "Editor", "ADMIN");
    }

    @Test
    void draftLifecycleKeepsPublicDataAndTaxonomyIsolated() throws Exception {
        mockMvc.perform(get("/api/admin/posts"))
                .andExpect(status().isUnauthorized());

        String token = login();
        String postBody = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Draft article",
                "content", "  raw markdown\n\nkeeps its spacing  ",
                "category", "Private category",
                "tags", java.util.List.of("Private tag")));

        String createResponse = mockMvc.perform(post("/api/admin/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.content").value("  raw markdown\n\nkeeps its spacing  "))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).path("data").path("id").asLong();
        String slug = objectMapper.readTree(createResponse).path("data").path("slug").asText();

        mockMvc.perform(get("/api/posts/" + slug)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$.data", not(hasItem("Private category"))));
        mockMvc.perform(get("/api/tags"))
                .andExpect(jsonPath("$.data", not(hasItem("Private tag"))));
        mockMvc.perform(post("/api/posts/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Reader\",\"content\":\"Hidden\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/posts/" + id + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/posts/" + slug)).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$.data", hasItem("Private category")));
        mockMvc.perform(get("/api/tags"))
                .andExpect(jsonPath("$.data", hasItem("Private tag")));

        mockMvc.perform(patch("/api/admin/posts/" + id + "/unpublish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(get("/api/posts/" + slug)).andExpect(status().isNotFound());
    }

    @Test
    void commentsRequirePublishedPostsAndCascadeOnDelete() throws Exception {
        String token = login();
        long id = createPublishedPost(token, "Commented article", "commented-article");

        mockMvc.perform(post("/api/posts/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\" Reader \",\"content\":\" First! \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.author").value("Reader"));
        String oversizedComment = objectMapper.writeValueAsString(java.util.Map.of(
                "author", "Reader", "content", "x".repeat(1001)));
        mockMvc.perform(post("/api/posts/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedComment))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/posts/" + id + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/api/admin/comments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        long commentId = commentRepository.findByPost(id).get(0).getId();
        mockMvc.perform(delete("/api/admin/comments/" + commentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + id + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Second reader\",\"content\":\"Keep until article deletion\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/posts/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(commentRepository.findByPost(id)).isEmpty();
    }

    @Test
    void numericSlugHasAnUnambiguousPublicRouteAndEditsKeepSlugAndViews() throws Exception {
        String token = login();
        long id = createPublishedPost(token, "Numeric", "123");

        mockMvc.perform(get("/api/posts/slug/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.viewCount").value(1));

        String rawContent = "    code block first line\n\nchanged\n";
        String update = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Renamed",
                "content", rawContent,
                "category", "Testing",
                "status", "PUBLISHED"));
        mockMvc.perform(put("/api/admin/posts/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("123"))
                .andExpect(jsonPath("$.data.content").value(rawContent))
                .andExpect(jsonPath("$.data.viewCount").value(1));
    }

    @Test
    void pageNumberOverflowReturnsAnEmptyPage() throws Exception {
        mockMvc.perform(get("/api/posts?page=2147483647&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2147483647))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"editor\",\"password\":\"correct horse battery staple\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private long createPublishedPost(String token, String title, String slug) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "title", title,
                "slug", slug,
                "content", "content",
                "category", "Testing",
                "status", "PUBLISHED"));
        String response = mockMvc.perform(post("/api/admin/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }
}
