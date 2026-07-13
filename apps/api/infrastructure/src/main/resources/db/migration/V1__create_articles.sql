CREATE TABLE articles (
  article_id VARCHAR(36) PRIMARY KEY,
  status VARCHAR(16) NOT NULL,
  slug VARCHAR(255) NOT NULL UNIQUE,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  published_at BIGINT,
  last_applied_seq BIGINT NOT NULL,
  CONSTRAINT articles_status_check CHECK (status IN ('draft', 'published')),
  CONSTRAINT articles_title_check CHECK (title <> ''),
  CONSTRAINT articles_published_at_check CHECK (
    (status = 'draft' AND published_at IS NULL)
    OR (status = 'published' AND published_at IS NOT NULL)
  ),
  CONSTRAINT articles_last_applied_seq_check CHECK (last_applied_seq > 0)
);
