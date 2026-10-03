package com.example.blog.post.repository;

import com.example.blog.post.model.Post;
import com.example.blog.post.model.PostStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPostRepository implements PostRepository {
    private static final String SELECT_POST = """
            SELECT id, title, slug, excerpt, content, category, cover_image, status,
                   created_at, updated_at, published_at, view_count,
                   (SELECT COUNT(*) FROM post_likes WHERE post_id = posts.id) AS like_count,
                   (SELECT COUNT(*) FROM comments WHERE post_id = posts.id AND status = 'APPROVED') AS comment_count
            FROM posts
            """;

    private final JdbcTemplate jdbc;
    private final RowMapper<Post> postMapper = (rs, rowNum) -> {
        Post post = new Post();
        post.setId(rs.getLong("id"));
        post.setTitle(rs.getString("title"));
        post.setSlug(rs.getString("slug"));
        post.setExcerpt(rs.getString("excerpt"));
        post.setContent(rs.getString("content"));
        post.setCategory(rs.getString("category"));
        post.setCoverImage(rs.getString("cover_image"));
        post.setStatus(PostStatus.valueOf(rs.getString("status")));
        post.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        post.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        post.setPublishedAt(toInstant(rs.getTimestamp("published_at")));
        post.setViewCount(rs.getLong("view_count"));
        post.setLikeCount(rs.getLong("like_count"));
        post.setCommentCount(rs.getLong("comment_count"));
        return post;
    };

    public JdbcPostRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Post> findAll() {
        List<Post> posts = jdbc.query(SELECT_POST, postMapper);
        posts.forEach(this::loadTags);
        return posts;
    }

    @Override
    public Optional<Post> findById(Long id) {
        return queryOne(SELECT_POST + " WHERE id = ?", id);
    }

    @Override
    public Optional<Post> findBySlug(String slug) {
        if (slug == null) return Optional.empty();
        return queryOne(SELECT_POST + " WHERE LOWER(slug) = LOWER(?)", slug);
    }

    @Override
    @Transactional
    public Post save(Post post) {
        if (post.getId() == null) {
            insert(post);
        } else {
            update(post);
        }
        replaceTags(post);
        return findById(post.getId()).orElseThrow();
    }

    @Override
    @Transactional
    public Post incrementViewCount(Long id) {
        int changed = jdbc.update("UPDATE posts SET view_count = view_count + 1 WHERE id = ?", id);
        if (changed == 0) throw new org.springframework.dao.EmptyResultDataAccessException(1);
        return findById(id).orElseThrow();
    }

    @Override
    public void deleteById(Long id) {
        jdbc.update("DELETE FROM posts WHERE id = ?", id);
    }

    private Optional<Post> queryOne(String sql, Object... args) {
        List<Post> posts = jdbc.query(sql, postMapper, args);
        if (posts.isEmpty()) return Optional.empty();
        Post post = posts.get(0);
        loadTags(post);
        return Optional.of(post);
    }

    private void insert(Post post) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO posts
                        (title, slug, excerpt, content, category, cover_image, status,
                         created_at, updated_at, published_at, view_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bindPost(statement, post, true);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a post id");
        post.setId(key.longValue());
    }

    private void update(Post post) {
        int changed = jdbc.update("""
                UPDATE posts
                SET title = ?, slug = ?, excerpt = ?, content = ?, category = ?, cover_image = ?,
                    status = ?, updated_at = ?, published_at = ?
                WHERE id = ?
                """,
                post.getTitle(), post.getSlug(), post.getExcerpt(), post.getContent(), post.getCategory(),
                post.getCoverImage(), post.getStatus().name(), timestamp(post.getUpdatedAt()),
                timestamp(post.getPublishedAt()), post.getId());
        if (changed == 0) throw new org.springframework.dao.EmptyResultDataAccessException(1);
    }

    private void bindPost(PreparedStatement statement, Post post, boolean includeViews) throws java.sql.SQLException {
        statement.setString(1, post.getTitle());
        statement.setString(2, post.getSlug());
        statement.setString(3, post.getExcerpt());
        statement.setString(4, post.getContent());
        statement.setString(5, post.getCategory());
        statement.setString(6, post.getCoverImage());
        statement.setString(7, post.getStatus().name());
        statement.setTimestamp(8, timestamp(post.getCreatedAt()));
        statement.setTimestamp(9, timestamp(post.getUpdatedAt()));
        statement.setTimestamp(10, timestamp(post.getPublishedAt()));
        if (includeViews) statement.setLong(11, post.getViewCount());
    }

    private void replaceTags(Post post) {
        jdbc.update("DELETE FROM post_tags WHERE post_id = ?", post.getId());
        List<String> tags = post.getTags();
        for (int i = 0; i < tags.size(); i++) {
            jdbc.update("INSERT INTO post_tags (post_id, tag, position) VALUES (?, ?, ?)",
                    post.getId(), tags.get(i), i);
        }
    }

    private void loadTags(Post post) {
        post.setTags(jdbc.query("SELECT tag FROM post_tags WHERE post_id = ? ORDER BY position",
                (rs, rowNum) -> rs.getString(1), post.getId()));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
