-- ========================================
-- Database Initialization Script
-- ========================================
-- This script runs automatically when the Postgres container
-- is first created (via docker-entrypoint-initdb.d).
--
-- It creates:
-- 1. The 'authz' database
-- 2. The 'authz' user with secure password
-- 3. Grants all privileges to the authz user
-- ========================================

-- Create the authz database
CREATE DATABASE authz
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

COMMENT ON DATABASE authz IS 'Authorization platform database';

-- Create the authz user
-- WARNING: This password is for LOCAL DEVELOPMENT ONLY
-- In production, use a strong password stored in a secret manager
CREATE USER authz WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    CONNECTION LIMIT -1
    PASSWORD 'authz';

-- Grant privileges on the authz database to the authz user
GRANT ALL PRIVILEGES ON DATABASE authz TO authz;

-- Connect to the authz database and set up schema permissions
\c authz

-- Grant schema usage and creation privileges
GRANT ALL ON SCHEMA public TO authz;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO authz;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO authz;

-- Ensure future objects are also accessible
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO authz;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO authz;

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'Database initialization completed successfully';
    RAISE NOTICE 'Database: authz';
    RAISE NOTICE 'User: authz';
    RAISE NOTICE 'Password: authz (CHANGE IN PRODUCTION!)';
END $$;
