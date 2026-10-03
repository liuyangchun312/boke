package com.example.blog.like.repository;

import com.example.blog.like.dto.LikeState;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class JdbcLikeRepository implements LikeRepository {
    private final JdbcTemplate jdbc;

    public JdbcLikeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LikeState findState(Long postId, String visitorId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS like_count,
                       COALESCE(MAX(CASE WHEN visitor_id = ? THEN 1 ELSE 0 END), 0) AS liked
                FROM post_likes WHERE post_id = ?
                """, (rs, rowNum) -> new LikeState(rs.getLong("like_count"), rs.getBoolean("liked")),
                visitorId, postId);
    }

    @Override
    public void setLiked(Long postId, String visitorId, boolean liked) {
        if (!liked) {
            jdbc.update("DELETE FROM post_likes WHERE post_id = ? AND visitor_id = ?", postId, visitorId);
            return;
        }
        try {
            jdbc.update("INSERT INTO post_likes (post_id, visitor_id, created_at) VALUES (?, ?, ?)",
                    postId, visitorId, Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException ignored) {
            // The primary key makes repeated and concurrent retries idempotent.
        }
    }

    @Override
    public void deleteByPost(Long postId) {
        jdbc.update("DELETE FROM post_likes WHERE post_id = ?", postId);
    }
}
