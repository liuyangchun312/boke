package com.example.blog;

import com.example.blog.auth.service.UserService;
import com.example.blog.comment.dto.CommentRequest;
import com.example.blog.comment.repository.CommentRepository;
import com.example.blog.comment.service.CommentService;
import com.example.blog.config.BootstrapProperties;
import com.example.blog.post.dto.PostRequest;
import com.example.blog.post.model.Post;
import com.example.blog.post.repository.PostRepository;
import com.example.blog.post.service.PostService;
import com.example.blog.seed.ProductionBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceRestartTests {
    @TempDir
    Path tempDir;

    @Test
    void postsTagsUsersAndViewsSurviveApplicationContextRestart() {
        String url = "jdbc:h2:file:" + tempDir.resolve("blog").toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
        long postId;
        String password = "a durable password";
        String rawContent = "    indented code first\n\nBody\n";

        try (ConfigurableApplicationContext first = context(url)) {
            UserService users = first.getBean(UserService.class);
            users.createIfAbsent("owner", password, "Owner", "ADMIN");
            PostService posts = first.getBean(PostService.class);
            PostRequest request = new PostRequest();
            request.setTitle("Durable post");
            request.setContent("raw\ncontent");
            request.setCategory("Journal");
            request.setTags(java.util.List.of("Durable", "H2"));
            request.setStatus(com.example.blog.post.model.PostStatus.PUBLISHED);
            postId = posts.create(request).getId();
            assertThat(posts.findPublishedByIdOrSlug(Long.toString(postId)).getViewCount()).isEqualTo(1);
            request.setTitle("Durable post revised");
            request.setContent(rawContent);
            posts.update(postId, request);
            Post staleEdit = first.getBean(PostRepository.class).findById(postId).orElseThrow();
            first.getBean(PostRepository.class).incrementViewCount(postId);
            staleEdit.setTitle("Concurrent edit");
            staleEdit.setUpdatedAt(java.time.Instant.now());
            first.getBean(PostRepository.class).save(staleEdit);
            first.getBean(CommentService.class).add(postId, new CommentRequest("Reader", "Persistent comment"));
        }

        try (ConfigurableApplicationContext second = context(url)) {
            Post persisted = second.getBean(PostRepository.class).findById(postId).orElseThrow();
            assertThat(persisted.getContent()).isEqualTo(rawContent);
            assertThat(persisted.getTags()).containsExactly("Durable", "H2");
            assertThat(persisted.getViewCount()).isEqualTo(2);
            assertThat(second.getBean(CommentRepository.class).findByPost(postId))
                    .extracting(comment -> comment.getContent())
                    .containsExactly("Persistent comment");
            UserService users = second.getBean(UserService.class);
            var persistedUser = users.find("OWNER").orElseThrow();
            assertThat(users.matches(persistedUser, password)).isTrue();
        }
    }

    @Test
    void productionBootstrapDisablesADevAccountPersistedInTheDatabase() throws Exception {
        String url = "jdbc:h2:mem:production-bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (ConfigurableApplicationContext context = context(url)) {
            UserService users = context.getBean(UserService.class);
            users.createIfAbsent("admin", "admin123", "Development Admin", "ADMIN");

            BootstrapProperties properties = new BootstrapProperties();
            properties.setUsername("site-owner");
            properties.setPassword("a production password");
            properties.setDisplayName("Site Owner");
            properties.setRole("ADMIN");
            new ProductionBootstrap(users, properties).run(null);

            assertThat(users.matches(users.find("admin").orElseThrow(), "admin123")).isFalse();
            assertThat(users.matches(users.find("site-owner").orElseThrow(), "a production password")).isTrue();
        }
    }

    @Test
    void databaseConstraintRejectsDuplicateSlugs() {
        String url = "jdbc:h2:mem:duplicate-slug;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (ConfigurableApplicationContext context = context(url)) {
            PostRepository repository = context.getBean(PostRepository.class);
            repository.save(post("duplicate"));
            assertThatThrownBy(() -> repository.save(post("duplicate")))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        }
    }

    private ConfigurableApplicationContext context(String url) {
        return new SpringApplicationBuilder(BlogApplication.class)
                .profiles("test")
                .run("--server.port=0",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver");
    }

    private Post post(String slug) {
        Post post = new Post();
        post.setTitle("Title");
        post.setSlug(slug);
        post.setExcerpt("Excerpt");
        post.setContent("Content");
        post.setCategory("Category");
        post.setStatus(com.example.blog.post.model.PostStatus.DRAFT);
        post.setCreatedAt(java.time.Instant.now());
        post.setUpdatedAt(java.time.Instant.now());
        return post;
    }
}
