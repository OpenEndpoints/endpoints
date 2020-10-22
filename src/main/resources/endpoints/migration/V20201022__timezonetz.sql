ALTER TABLE request_log
RENAME COLUMN datetime_utc TO datetime;

ALTER TABLE request_log
ALTER datetime TYPE timestamptz USING datetime AT TIME ZONE 'UTC';
