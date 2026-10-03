package com.example.blog;

import com.example.blog.post.dto.PostRequest;
import com.example.blog.post.model.PostStatus;
import com.example.blog.post.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InteractionPersistenceRestartTests {
    @TempDir Path directory;

    @Test
    void likesModerationAndRepliesSurviveApplicationRestart() throws Exception {
        String url = databaseUrl("interactions");
        long postId;
        long commentId;
        try (ConfigurableApplicationContext first = context(url)) {
            PostRequest request = new PostRequest();
            request.setTitle("Persistent interactions");
            request.setContent("Content");
            request.setCategory("Testing");
            request.setStatus(PostStatus.PUBLISHED);
            postId = first.getBean(PostService.class).create(request).getId();
            MockMvc mvc = mvc(first);
            mvc.perform(put("/api/posts/" + postId + "/likes").header("X-Visitor-Id", "persistent_visitor_1")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
                    .andExpect(status().isOk());
            String response = mvc.perform(post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"author\":\"Reader\",\"content\":\"Persistent comment\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            commentId = first.getBean(ObjectMapper.class).readTree(response).path("data").path("id").asLong();
            mvc.perform(patch("/api/admin/comments/" + commentId + "/status").with(user("editor").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/admin/comments/" + commentId + "/reply").with(user("editor").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Persistent reply\"}"))
                    .andExpect(status().isOk());
        }
        try (ConfigurableApplicationContext second = context(url)) {
            MockMvc mvc = mvc(second);
            mvc.perform(get("/api/posts/" + postId + "/likes").header("X-Visitor-Id", "persistent_visitor_1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1))
                    .andExpect(jsonPath("$.data.liked").value(true));
            mvc.perform(put("/api/posts/" + postId + "/likes").header("X-Visitor-Id", "persistent_visitor_1")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
                    .andExpect(jsonPath("$.data.likeCount").value(1));
            mvc.perform(get("/api/posts/" + postId + "/comments"))
                    .andExpect(jsonPath("$.data[0].id").value(commentId))
                    .andExpect(jsonPath("$.data[0].status").value("APPROVED"))
                    .andExpect(jsonPath("$.data[0].reply").value("Persistent reply"))
                    .andExpect(jsonPath("$.data[0].repliedAt").isString());
            mvc.perform(get("/api/admin/posts/" + postId).with(user("editor").roles("ADMIN")))
                    .andExpect(jsonPath("$.data.likeCount").value(1))
                    .andExpect(jsonPath("$.data.commentCount").value(1));
        }
    }

    @Test
    void migrationKeepsExistingCommentsApproved() throws Exception {
        String url = databaseUrl("legacy");
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        jdbc.update("INSERT INTO posts (title, slug, excerpt, content, category, status, created_at, updated_at, published_at) "
                + "VALUES ('Legacy', 'legacy', 'Legacy', 'Content', 'Testing', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        Long id = jdbc.queryForObject("SELECT id FROM posts WHERE slug = 'legacy'", Long.class);
        jdbc.update("INSERT INTO comments (post_id, author, content, created_at) VALUES (?, 'Reader', 'Legacy comment', CURRENT_TIMESTAMP)", id);
        try (ConfigurableApplicationContext context = context(url)) {
            mvc(context).perform(get("/api/posts/" + id + "/comments"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].content").value("Legacy comment"))
                    .andExpect(jsonPath("$.data[0].status").value("APPROVED"));
            mvc(context).perform(get("/api/posts/" + id)).andExpect(jsonPath("$.data.commentCount").value(1));
        }
    }

    private String databaseUrl(String name) {
        return "jdbc:h2:file:" + directory.resolve(name).toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
    }

    private ConfigurableApplicationContext context(String url) {
        return new SpringApplicationBuilder(BlogApplication.class).profiles("test")
                .run("--server.port=0", "--spring.datasource.url=" + url,
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver");
    }

    private MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
    }
}
