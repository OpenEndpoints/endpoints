-- Existing short links do not need to be migrated, they can be deleted.
ALTER TABLE short_link_to_endpoint
ADD expires_on TIMESTAMP WITH TIME ZONE;

UPDATE short_link_to_endpoint
SET expires_on = created_on + '1 year';

ALTER TABLE short_link_to_endpoint
ALTER expires_on SET NOT NULL;