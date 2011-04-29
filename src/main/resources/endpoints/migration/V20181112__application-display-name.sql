ALTER TABLE application_config
ADD COLUMN display_name VARCHAR UNIQUE;

UPDATE application_config
SET display_name = application_name;

ALTER TABLE application_config
ALTER COLUMN display_name SET NOT NULL;