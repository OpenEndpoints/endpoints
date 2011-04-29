ALTER TABLE request_log
ADD status_code INT NOT NULL,
ADD reason_phase VARCHAR NOT NULL;