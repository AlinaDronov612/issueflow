-- Seed data for IssueFlow.
-- Runs after Hibernate creates the schema from the JPA entities
-- (spring.jpa.defer-datasource-initialization=true, spring.sql.init.mode=always).
--
-- Bootstrap ADMIN user. Without this there is no way to obtain the first JWT,
-- since every endpoint except POST /auth/login requires authentication.
-- Credentials (documented in run.md):  username = admin  /  password = admin123
-- The password_hash below is a BCrypt hash of "admin123".
--
-- The INSERT ... WHERE NOT EXISTS form is idempotent and portable across
-- PostgreSQL (runtime) and H2 (tests), so repeated startups do not fail.
INSERT INTO users (username, email, full_name, role, password_hash, created_at, updated_at)
SELECT 'admin', 'admin@issueflow.local', 'IssueFlow Admin', 'ADMIN',
       '$2a$10$NdnbFmhgWmuvwL9swJmawu3n.kkv/Fi5IcY8fsHjbFgzvK3hX7bEu',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
