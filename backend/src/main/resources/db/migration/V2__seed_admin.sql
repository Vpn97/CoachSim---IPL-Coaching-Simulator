-- Seed an admin user. Password = "admin123" (BCrypt $2a$10$...). Change in production!
-- Hash generated with BCrypt strength 10 for "admin123":
INSERT INTO users (email, password_hash, display_name, role)
VALUES ('admin@coachsim.local',
        '$2a$10$Eo3o4HhPGfQB78v6vTLOWuFkRD16e0EH/SrgyJsM/B/EaQYHTxk0e',
        'CoachSim Admin',
        'ROLE_ADMIN')
ON CONFLICT (email) DO NOTHING;
