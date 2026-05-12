-- Runs once on first volume init. Flyway handles schema; we only bootstrap extensions.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
