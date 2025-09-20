CREATE TABLE IF NOT EXISTS user_roles (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(120) NOT NULL,
  role     VARCHAR(64)  NOT NULL,
  CONSTRAINT uk_user_role UNIQUE (username, role)
);

INSERT INTO user_roles (username, role) VALUES
  ('admin', 'ADMIN'),
  ('user',  'USER')
ON CONFLICT ON CONSTRAINT uk_user_role DO NOTHING;
