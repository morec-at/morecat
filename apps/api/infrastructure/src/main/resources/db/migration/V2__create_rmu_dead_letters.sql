CREATE TABLE rmu_dead_letters (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  event_id TEXT,
  event_type TEXT,
  source TEXT,
  body BYTEA NOT NULL,
  reason TEXT NOT NULL,
  deduplication_key BYTEA NOT NULL UNIQUE,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT rmu_dead_letters_reason_check CHECK (reason <> ''),
  CONSTRAINT rmu_dead_letters_deduplication_key_check CHECK (
    octet_length(deduplication_key) = 32
  )
);
