ALTER TABLE application_config
ADD IF NOT EXISTS git_url VARCHAR NOT NULL,
ADD IF NOT EXISTS git_username VARCHAR NULL,
ADD IF NOT EXISTS git_password_cleartext VARCHAR NULL,
ADD CONSTRAINT git_username_password CHECK ((git_username IS NULL) = (git_password_cleartext IS NULL));