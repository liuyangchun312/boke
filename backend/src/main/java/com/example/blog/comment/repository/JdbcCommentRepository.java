package com.example.blog.comment.repository;

import com.example.blog.comment.model.Comment;
import com.example.blog.comment.model.CommentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCommentRepository implements CommentRepository {
    private static final String SELECT_COMMENT = """
            SELECT id, post_id, author, content, created_at, status, reply, replied_at FROM comments
            """;
    private final JdbcTemplate jdbc;

    public JdbcCommentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Comment save(Comment comment) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO comments (post_id, author, content, created_at, status, reply, replied_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, comment.getPostId());
            statement.setString(2, comment.getAuthor());
            statement.setString(3, comment.getContent());
            statement.setTimestamp(4, Timestamp.from(comment.getCreatedAt()));
            statement.setString(5, comment.getStatus().name());
            statement.setString(6, comment.getReply());
            statement.setTimestamp(7, timestamp(comment.getRepliedAt()));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a comment id");
        return findById(key.longValue()).orElseThrow();
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return jdbc.query(SELECT_COMMENT + " WHERE id = ?", this::mapComment, id).stream().findFirst();
    }

    @Override
    public List<Comment> findByPost(Long postId) {
        return jdbc.query(SELECT_COMMENT + " WHERE post_id = ? ORDER BY created_at, id", this::mapComment, postId);
    }

    @Override
    public List<Comment> findApprovedByPost(Long postId) {
        return jdbc.query(SELECT_COMMENT + " WHERE post_id = ? AND status = 'APPROVED' ORDER BY created_at, id",
                this::mapComment, postId);
    }

    @Override
    public List<Comment> findAll() {
        return jdbc.query(SELECT_COMMENT + " ORDER BY created_at DESC, id DESC", this::mapComment);
    }

    @Override
    public boolean updateStatus(Long id, CommentStatus status) {
        return jdbc.update("UPDATE comments SET status = ? WHERE id = ?", status.name(), id) > 0;
    }

    @Override
    public boolean updateReply(Long id, String reply, Instant repliedAt) {
        return jdbc.update("UPDATE comments SET reply = ?, replied_at = ? WHERE id = ?",
                reply, timestamp(repliedAt), id) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return jdbc.update("DELETE FROM comments WHERE id = ?", id) > 0;
    }

    private Comment mapComment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Comment comment = new Comment(rs.getLong("id"), rs.getLong("post_id"),
                rs.getString("author"), rs.getString("content"),
                rs.getTimestamp("created_at").toInstant());
        comment.setStatus(CommentStatus.valueOf(rs.getString("status")));
        comment.setReply(rs.getString("reply"));
        Timestamp repliedAt = rs.getTimestamp("replied_at");
        comment.setRepliedAt(repliedAt == null ? null : repliedAt.toInstant());
        return comment;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
