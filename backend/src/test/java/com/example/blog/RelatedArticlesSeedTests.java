package com.example.blog;

import com.example.blog.post.dto.PostRequest;
import com.example.blog.post.model.Post;
import com.example.blog.post.model.PostStatus;
import com.example.blog.post.repository.PostRepository;
import com.example.blog.post.service.PostService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelatedArticlesSeedTests {
    @TempDir
    Path tempDir;

    @Test
    void existingDatabaseReceivesTenArticlesAndKeepsAnExistingMatchingSlug() {
        String url = database("existing");
        long matchingId;
        try (ConfigurableApplicationContext initial = context(url, "test")) {
            PostService posts = initial.getBean(PostService.class);
            posts.create(request("Owner's existing post", "owners-existing-post", "Original body"));
            matchingId = posts.create(request("Owner's revised article", "taihe-farming-season-notes",
                    "Owner-written content must survive the import")).getId();
        }

        try (ConfigurableApplicationContext seeded = context(url, "dev")) {
            PostRepository posts = seeded.getBean(PostRepository.class);
            assertThat(posts.findAll()).hasSize(11);
            assertThat(posts.findById(matchingId).orElseThrow().getContent())
                    .isEqualTo("Owner-written content must survive the import");
            assertThat(posts.findBySlug("taihe-black-bone-chicken-wushan")).isEmpty();
            assertThat(posts.findBySlug("ganjiang-riverside-walking-notes").orElseThrow().getCreatedAt())
                    .isEqualTo(Instant.parse("2026-09-03T00:30:00Z"));
        }
    }

    @Test
    void restartingDoesNotOverwriteEditsOrRestoreDeletedRelatedArticles() {
        String url = database("restart");
        long editedId;
        String deletedSlug = "taihe-autumn-photography-notes";
        try (ConfigurableApplicationContext initial = context(url, "dev")) {
            PostRepository posts = initial.getBean(PostRepository.class);
            assertThat(posts.findAll()).hasSize(15);
            editedId = posts.findBySlug("taihe-farming-season-notes").orElseThrow().getId();
            initial.getBean(PostService.class).update(editedId,
                    request("Revised by owner", "renamed-by-owner", "An edited article with a new slug"));
            initial.getBean(PostService.class).delete(posts.findBySlug(deletedSlug).orElseThrow().getId());
        }

        try (ConfigurableApplicationContext restarted = context(url, "dev")) {
            PostRepository posts = restarted.getBean(PostRepository.class);
            assertThat(posts.findAll()).hasSize(14);
            assertThat(posts.findById(editedId).orElseThrow().getContent())
                    .isEqualTo("An edited article with a new slug");
            assertThat(posts.findBySlug("taihe-farming-season-notes")).isEmpty();
            assertThat(posts.findBySlug(deletedSlug)).isEmpty();
        }
    }

    @Test
    void manifestContainsTenSubstantialPublishedArticlesWithDistinctPastDates() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode articles;
        try (var input = new ClassPathResource("seed/related-articles.json").getInputStream()) {
            articles = mapper.readTree(input);
        }
        assertThat(articles.size()).isEqualTo(10);
        var slugs = new HashSet<String>();
        var dates = new HashSet<String>();
        for (JsonNode article : articles) {
            assertThat(slugs.add(article.path("slug").asText())).isTrue();
            assertThat(dates.add(article.path("createdAt").asText())).isTrue();
            assertThat(article.path("status").asText()).isEqualTo("PUBLISHED");
            assertThat(Instant.parse(article.path("createdAt").asText()))
                    .isBefore(Instant.parse("2026-10-03T00:00:00Z"));
            String content = article.path("content").asText();
            long chineseCharacters = content.codePoints()
                    .filter(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN).count();
            assertThat(chineseCharacters).as(article.path("title").asText()).isGreaterThanOrEqualTo(600);
            assertThat(content.split("(?m)^## ", -1).length - 1).isGreaterThanOrEqualTo(3);
            assertThat(article.path("coverImage").asText()).startsWith("/covers/taihe-");
            assertThat(article.path("tags").size()).isGreaterThanOrEqualTo(2);
        }
    }

    private String database(String name) {
        return "jdbc:h2:file:" + tempDir.resolve(name).toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
    }

    private ConfigurableApplicationContext context(String url, String profile) {
        return new SpringApplicationBuilder(BlogApplication.class).profiles(profile)
                .run("--server.port=0", "--spring.datasource.url=" + url,
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver");
    }

    private PostRequest request(String title, String slug, String content) {
        PostRequest request = new PostRequest();
        request.setTitle(title);
        request.setSlug(slug);
        request.setContent(content);
        request.setCategory("Owner's category");
        request.setTags(List.of("Owner's tag"));
        request.setStatus(PostStatus.PUBLISHED);
        return request;
    }
}
