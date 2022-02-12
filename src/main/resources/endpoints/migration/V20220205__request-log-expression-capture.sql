CREATE TABLE request_log_expression_capture (
  request_id   UUID    NOT NULL REFERENCES request_log_ids,
  key          VARCHAR NOT NULL,
  value        VARCHAR NOT NULL,
  PRIMARY KEY (request_id, key)
);

CREATE INDEX request_log_expression_capture_order
ON request_log_expression_capture(request_id, LOWER(key));