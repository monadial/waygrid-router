-- File: .docker/postgres/initdb.d/00_init.sql
CREATE ROLE replica WITH REPLICATION LOGIN PASSWORD 'replica_pass';
ALTER SYSTEM SET listen_addresses = '*';

-- Create databases
CREATE DATABASE waygrid_system_scheduler;
CREATE DATABASE waygrid_system_topology;

-- Create user if not exists (will already be created by POSTGRES_USER env var, but no harm)
DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT FROM pg_catalog.pg_roles WHERE rolname = 'waygrid'
        ) THEN
            CREATE ROLE waygrid LOGIN PASSWORD 'Start@123';
        END IF;
    END
$$;

-- Grant privileges on new databases
GRANT ALL PRIVILEGES ON DATABASE waygrid_system_scheduler TO waygrid;
GRANT ALL PRIVILEGES ON DATABASE waygrid_system_topology TO waygrid;
