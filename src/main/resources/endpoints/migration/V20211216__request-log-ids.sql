CREATE TABLE request_log_ids (
  request_id                              UUID PRIMARY KEY,
  application                             VARCHAR NOT NULL,
  environment                             VARCHAR NOT NULL,
  endpoint                                VARCHAR NOT NULL,
  incremental_id_per_endpoint             BIGINT,
  random_id_per_application               BIGINT,
  on_demand_perpetual_incrementing_number INTEGER,
  on_demand_month_incrementing_number     INTEGER,
  on_demand_year_incrementing_number      INTEGER,
  CONSTRAINT request_log_ids_app_environment_on_demand_perpetual_inc_key 
    UNIQUE (application, environment, on_demand_perpetual_incrementing_number)
);

INSERT INTO request_log_ids
SELECT
  request_id,
  application,
  environment,
  endpoint,
  incremental_id_per_endpoint,           
  random_id_per_application,          
  on_demand_perpetual_incrementing_number,
  on_demand_month_incrementing_number,
  on_demand_year_incrementing_number
FROM request_log;

CREATE INDEX "request_log_ids_endpoint" ON request_log_ids(application, environment, endpoint);
CREATE INDEX "request_log_ids_incremental" ON request_log_ids(application, environment, endpoint, incremental_id_per_endpoint);
CREATE INDEX "request_log_ids_random" ON request_log_ids(application, environment, random_id_per_application);

ALTER TABLE request_log
DROP application,
DROP environment,
DROP endpoint,
DROP incremental_id_per_endpoint,
DROP random_id_per_application,
DROP on_demand_perpetual_incrementing_number,
DROP on_demand_month_incrementing_number,
DROP on_demand_year_incrementing_number,
ADD FOREIGN KEY (request_id) REFERENCES request_log_ids (request_id),
ADD PRIMARY KEY (request_id);

CREATE INDEX "request_log_date" ON request_log(datetime);
CREATE INDEX "request_log_status_date" ON request_log(status_code, datetime);
