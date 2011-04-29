CREATE TABLE service_portal_login (
  username VARCHAR NOT NULL PRIMARY KEY,
  password_bcrypt VARCHAR NOT NULL
);

CREATE TABLE service_portal_login_application (
  username VARCHAR NOT NULL REFERENCES service_portal_login,
  application_name VARCHAR NOT NULL REFERENCES application_config,
  PRIMARY KEY (username, application_name)
);

