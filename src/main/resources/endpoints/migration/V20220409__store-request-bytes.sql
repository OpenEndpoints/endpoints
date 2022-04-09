ALTER TABLE request_log
ADD request_content_type VARCHAR NULL,
ADD request_body BYTEA NULL;