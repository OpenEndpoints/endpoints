INSERT INTO service_portal_login 
VALUES ('admin', '$2a$10$VOBc53Fu0louc.K4AGLUJuiTdbPimluy4feYeShLIrOrQy//U.UpO', true, true) -- password is "admin"
ON CONFLICT DO NOTHING;