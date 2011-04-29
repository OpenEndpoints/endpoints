ALTER TABLE request_log
ADD on_demand_perpetual_incrementing_number INTEGER NULL,
ADD UNIQUE (application, environment, on_demand_perpetual_incrementing_number),
ADD on_demand_month_incrementing_number INTEGER NULL,
ADD UNIQUE (application, environment, on_demand_month_incrementing_number),
ADD on_demand_year_incrementing_number INTEGER NULL,
ADD UNIQUE (application, environment, on_demand_year_incrementing_number);

ALTER TABLE application_config
ADD timezone VARCHAR DEFAULT 'Europe/Vienna' NOT NULL;
