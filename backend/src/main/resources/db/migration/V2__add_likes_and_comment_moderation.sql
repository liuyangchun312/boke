CREATE TABLE post_likes (
    post_id BIGINT NOT NULL,
    visitor_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (post_id, visitor_id),
    CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

ALTER TABLE comments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE comments ADD COLUMN reply VARCHAR(1000);
ALTER TABLE comments ADD COLUMN replied_at TIMESTAMP(6);

CREATE INDEX idx_comments_post_status_created ON comments(post_id, status, created_at);
