CREATE TABLE IF NOT EXISTS security_audit (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(120),
  action VARCHAR(120) NOT NULL,
  ip VARCHAR(120),
  user_agent VARCHAR(255),
  details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_user_time ON security_audit(username, created_at);
