CREATE TABLE request_log (
   application VARCHAR NOT NULL,
   endpoint VARCHAR NOT NULL,
   datetime_utc TIMESTAMP NOT NULL,
   ip_address VARCHAR NOT NULL
);
