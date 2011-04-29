CREATE TABLE application_config (
  application_name VARCHAR PRIMARY KEY,
  locked BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO application_config(application_name)
SELECT application_name
FROM application_publish;

ALTER TABLE application_publish
ADD CONSTRAINT application_publish_appliation
FOREIGN KEY (application_name)
REFERENCES application_config(application_name);
