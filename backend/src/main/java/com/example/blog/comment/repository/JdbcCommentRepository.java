package com.example.blog.comment.repository;

import com.example.blog.comment.model.Comment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcCommentRepository implements CommentRepository {
    private final JdbcTemplate jdbc;

    public JdbcCommentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Comment save(Comment comment) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO comments (post_id, author, content, created_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, comment.getPostId());
            statement.setString(2, comment.getAuthor());
            statement.setString(3, comment.getContent());
            statement.setTimestamp(4, Timestamp.from(comment.getCreatedAt()));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a comment id");
        return new Comment(key.longValue(), comment.getPostId(), comment.getAuthor(),
                comment.getContent(), comment.getCreatedAt());
    }

    @Override
    public List<Comment> findByPost(Long postId) {
        return jdbc.query("""
                        SELECT id, post_id, author, content, created_at
                        FROM comments WHERE post_id = ? ORDER BY created_at, id
                        """, this::mapComment, postId);
    }

    @Override
    public List<Comment> findAll() {
        return jdbc.query("""
                SELECT id, post_id, author, content, created_at
                FROM comments ORDER BY created_at DESC, id DESC
                """, this::mapComment);
    }

    @Override
    public boolean deleteById(Long id) {
        return jdbc.update("DELETE FROM comments WHERE id = ?", id) > 0;
    }

    private Comment mapComment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Comment(rs.getLong("id"), rs.getLong("post_id"),
                rs.getString("author"), rs.getString("content"),
                rs.getTimestamp("created_at").toInstant());
    }
}
