ALTER TABLE service_portal_login
ADD must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
ADD CONSTRAINT must_change_password_is_only_for_admin CHECK (NOT must_change_password OR admin);