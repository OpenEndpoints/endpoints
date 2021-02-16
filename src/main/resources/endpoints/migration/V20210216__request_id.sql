ALTER TABLE request_log
DROP request_log_id,
ADD request_id UUID NOT NULL DEFAULT md5(random()::text)::uuid;

ALTER TABLE request_log
ALTER request_id DROP DEFAULT;