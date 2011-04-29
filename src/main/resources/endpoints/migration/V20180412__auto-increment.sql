CREATE TABLE auto_increment (
  application VARCHAR NOT NULL,
  endpoint VARCHAR NOT NULL,
  last_value BIGINT NOT NULL,
  PRIMARY KEY (application, endpoint)
);

ALTER TABLE request_log
ADD incremental_id_per_endpoint BIGINT NULL,
ADD random_id_per_application BIGINT NULL;
