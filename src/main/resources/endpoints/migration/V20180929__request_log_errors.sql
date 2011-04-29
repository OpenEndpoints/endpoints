ALTER TABLE request_log
RENAME COLUMN reason_phase TO exception_message;

ALTER TABLE request_log
ALTER COLUMN exception_message DROP NOT NULL,
ADD COLUMN http_request_failed_url VARCHAR NULL,
ADD COLUMN http_request_failed_status_code INTEGER NULL,
ADD COLUMN xslt_parameter_error_message VARCHAR NULL;
