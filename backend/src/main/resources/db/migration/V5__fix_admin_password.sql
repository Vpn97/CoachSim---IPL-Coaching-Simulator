-- V5: Repair the admin password.
--
-- V2 seeded the admin user with a hard-coded BCrypt hash that did NOT actually
-- correspond to "admin123" (verified via pgcrypto: crypt('admin123', hash) != hash).
-- We can't edit V2 (Flyway tracks its checksum once applied), so we re-hash here
-- using pgcrypto, mirroring how V4 seeds the demo fans.
--
-- This migration is idempotent: re-running yields a different salt but the same
-- effective password. The CREATE EXTENSION guard makes it safe in fresh DBs too.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE users
   SET password_hash = crypt('admin123', gen_salt('bf', 10))
 WHERE email = 'admin@coachsim.local';
