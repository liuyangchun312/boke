package com.example.blog;

import com.example.blog.post.dto.PostRequest;
import com.example.blog.post.model.PostStatus;
import com.example.blog.post.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog-interactions;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InteractionIntegrationTests {
    private static final String VISITOR = "visitor_1234567890";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private PostService posts;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clearPosts() {
        jdbc.update("DELETE FROM posts");
    }

    @Test
    void likesAreIdempotentPerVisitorAndAppearInPublicAndAdminPosts() throws Exception {
        long id = createPost(PostStatus.PUBLISHED);
        mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.liked").value(false));
        setLike(id, VISITOR, true);
        setLike(id, VISITOR, true);
        mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.liked").value(true));
        mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", "another_1234567890"))
                .andExpect(jsonPath("$.data.liked").value(false));
        setLike(id, "another_1234567890", true);
        mvc.perform(get("/api/posts/" + id)).andExpect(jsonPath("$.data.likeCount").value(2))
                .andExpect(jsonPath("$.data.commentCount").value(0));
        mvc.perform(get("/api/admin/posts/" + id).with(user("editor").roles("ADMIN")))
                .andExpect(jsonPath("$.data.likeCount").value(2));
        setLike(id, VISITOR, false);
        setLike(id, VISITOR, false);
        mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.liked").value(false));
        mvc.perform(delete("/api/admin/posts/" + id + "/likes").with(user("editor").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", "another_1234567890"))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.liked").value(false));
    }

    @Test
    void concurrentRetriesCannotCreateDuplicateLikes() throws Exception {
        long id = createPost(PostStatus.PUBLISHED);
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Callable<String>> retries = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                retries.add(() -> mvc.perform(put("/api/posts/" + id + "/likes")
                                .header("X-Visitor-Id", VISITOR).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"liked\":true}"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            }
            for (var result : executor.invokeAll(retries)) {
                var state = json.readTree(result.get()).path("data");
                assertThat(state.path("liked").asBoolean()).isTrue();
                assertThat(state.path("likeCount").asLong()).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
        mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR))
                .andExpect(jsonPath("$.data.likeCount").value(1));
    }

    @Test
    void publicInteractionsRejectDraftAndMissingPosts() throws Exception {
        for (long id : new long[]{createPost(PostStatus.DRAFT), Long.MAX_VALUE}) {
            mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/posts/" + id + "/comments")).andExpect(status().isNotFound());
            mvc.perform(post("/api/posts/" + id + "/comments").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"author\":\"Reader\",\"content\":\"Comment\"}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/admin/posts/" + Long.MAX_VALUE + "/likes").with(user("editor").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void likesRequireAValidVisitorAndExplicitBooleanState() throws Exception {
        long id = createPost(PostStatus.PUBLISHED);
        mvc.perform(get("/api/posts/" + id + "/likes")).andExpect(status().isBadRequest());
        for (String visitor : List.of("short", "visitor!123456789", "x".repeat(129))) {
            mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", visitor))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/posts/" + id + "/likes").header("X-Visitor-Id", visitor)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
                    .andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "{\"liked\":null}", "{\"liked\":{}}")) {
            mvc.perform(put("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void likesRejectStringAndNumberBooleanCoercion() throws Exception {
        long id = createPost(PostStatus.PUBLISHED);
        for (String body : List.of("{\"liked\":\"true\"}", "{\"liked\":1}", "{\"liked\":\"false\"}", "{\"liked\":0}")) {
            mvc.perform(put("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/posts/" + id + "/likes").header("X-Visitor-Id", VISITOR))
                .andExpect(jsonPath("$.data.likeCount").value(0));
    }

    @Test
    void commentsWaitForApprovalAndAdminRepliesFollowTheirVisibility() throws Exception {
        long id = createPost(PostStatus.PUBLISHED);
        long commentId = addComment(id);
        mvc.perform(get("/api/posts/" + id + "/comments"))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/posts/" + id)).andExpect(jsonPath("$.data.commentCount").value(0));
        mvc.perform(get("/api/admin/comments").with(user("editor").roles("ADMIN")))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
        changeStatus(commentId, "APPROVED");
        mvc.perform(put("/api/admin/comments/" + commentId + "/reply").with(user("editor").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  Thank you  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reply").value("Thank you"))
                .andExpect(jsonPath("$.data.repliedAt").isString());
        mvc.perform(get("/api/posts/" + id + "/comments"))
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].reply").value("Thank you"));
        mvc.perform(get("/api/posts/" + id)).andExpect(jsonPath("$.data.commentCount").value(1));
        mvc.perform(get("/api/admin/posts").with(user("editor").roles("ADMIN")))
                .andExpect(jsonPath("$.data.items[0].commentCount").value(1));
        changeStatus(commentId, "HIDDEN");
        mvc.perform(get("/api/posts/" + id + "/comments")).andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/posts")).andExpect(jsonPath("$.data.items[0].commentCount").value(0));
        mvc.perform(get("/api/admin/comments").with(user("editor").roles("ADMIN")))
                .andExpect(jsonPath("$.data[0].status").value("HIDDEN"));
        mvc.perform(put("/api/admin/comments/" + commentId + "/reply").with(user("editor").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reply").doesNotExist())
                .andExpect(jsonPath("$.data.repliedAt").doesNotExist());
        changeStatus(commentId, "PENDING");
    }

    @Test
    void adminChangesValidateStatusRepliesAndMissingComments() throws Exception {
        long id = addComment(createPost(PostStatus.PUBLISHED));
        for (String body : List.of("{}", "{\"status\":null}", "{\"status\":\"INVALID\"}", "{\"status\":1}", "{\"status\":\"1\"}")) {
            mvc.perform(patch("/api/admin/comments/" + id + "/status").with(user("editor").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "{\"content\":null}", json.writeValueAsString(java.util.Map.of("content", "x".repeat(1001))))) {
            mvc.perform(put("/api/admin/comments/" + id + "/reply").with(user("editor").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/admin/comments/" + Long.MAX_VALUE + "/status").with(user("editor").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/comments/" + Long.MAX_VALUE + "/reply").with(user("editor").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"reply\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminRepliesRejectNonStringContent() throws Exception {
        long id = addComment(createPost(PostStatus.PUBLISHED));
        for (String body : List.of("{\"content\":1}", "{\"content\":true}")) {
            mvc.perform(put("/api/admin/comments/" + id + "/reply").with(user("editor").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/comments").with(user("editor").roles("ADMIN")))
                .andExpect(jsonPath("$.data[0].reply").doesNotExist());
    }

    @Test
    void moderationRequiresAdminAndVisitorHeaderWorksWithCors() throws Exception {
        long postId = createPost(PostStatus.PUBLISHED);
        long commentId = addComment(postId);
        mvc.perform(delete("/api/admin/posts/" + postId + "/likes")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/comments/" + commentId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/comments/" + commentId + "/reply").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"reply\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/posts/" + postId + "/likes").with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/posts/" + postId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(options("/api/posts/" + postId + "/likes").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "content-type,x-visitor-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type, x-visitor-id"));
    }

    @Test
    void deletingCommentsAndPostsRemovesTheirInteractionData() throws Exception {
        long postId = createPost(PostStatus.PUBLISHED);
        long commentId = addComment(postId);
        changeStatus(commentId, "APPROVED");
        setLike(postId, VISITOR, true);
        mvc.perform(delete("/api/admin/comments/" + commentId).with(user("editor").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.commentCount").value(0));
        addComment(postId);
        mvc.perform(delete("/api/admin/posts/" + postId).with(user("editor").roles("ADMIN")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Long.class, postId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id = ?", Long.class, postId)).isZero();
    }

    private long createPost(PostStatus status) {
        PostRequest request = new PostRequest();
        request.setTitle("Interaction article");
        request.setContent("Article content");
        request.setCategory("Testing");
        request.setStatus(status);
        return posts.create(request).getId();
    }

    private void setLike(long id, String visitor, boolean liked) throws Exception {
        mvc.perform(put("/api/posts/" + id + "/likes").header("X-Visitor-Id", visitor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":" + liked + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(liked));
    }

    private long addComment(long id) throws Exception {
        String result = mvc.perform(post("/api/posts/" + id + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\" Reader \",\"content\":\" Useful article \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.author").value("Reader"))
                .andExpect(jsonPath("$.data.content").value("Useful article"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reply").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(result).path("data").path("id").asLong();
    }

    private void changeStatus(long id, String value) throws Exception {
        mvc.perform(patch("/api/admin/comments/" + id + "/status").with(user("editor").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(value));
    }
}
