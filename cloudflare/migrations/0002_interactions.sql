CREATE TABLE post_likes (
  postId INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  visitorId TEXT NOT NULL,
  createdAt TEXT NOT NULL,
  PRIMARY KEY (postId, visitorId)
);
ALTER TABLE comments ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'APPROVED', 'HIDDEN'));
UPDATE comments SET status = 'APPROVED';
ALTER TABLE comments ADD COLUMN reply TEXT;
ALTER TABLE comments ADD COLUMN repliedAt TEXT;
CREATE INDEX comments_post_status ON comments(postId, status, createdAt DESC, id DESC);
