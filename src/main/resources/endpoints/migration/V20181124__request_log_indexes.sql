CREATE INDEX request_log_date                 ON request_log(application, environment, datetime_utc);
CREATE INDEX request_log_endpoint_date        ON request_log(application, environment, endpoint, datetime_utc);
CREATE INDEX request_log_endpoint_status_date ON request_log(application, environment, endpoint, status_code, datetime_utc);
CREATE INDEX request_log_status_date          ON request_log(application, environment, status_code, datetime_utc);
