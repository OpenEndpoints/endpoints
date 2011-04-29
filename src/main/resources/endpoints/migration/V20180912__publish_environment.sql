ALTER TABLE application
ADD environment VARCHAR NOT NULL DEFAULT 'live';

ALTER TABLE application
ALTER COLUMN environment DROP DEFAULT;

ALTER TABLE application DROP CONSTRAINT pk_application;

ALTER TABLE application
ADD PRIMARY KEY (application, environment);

--

ALTER TABLE request_log
ADD COLUMN environment VARCHAR NOT NULL DEFAULT 'live';

ALTER TABLE request_log
ALTER COLUMN environment DROP DEFAULT;
