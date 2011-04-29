CREATE INDEX request_log_incremental ON request_log(application, endpoint, incremental_id_per_endpoint);

CREATE INDEX request_log_random ON request_Log(application, random_id_per_application);

DROP TABLE auto_increment;