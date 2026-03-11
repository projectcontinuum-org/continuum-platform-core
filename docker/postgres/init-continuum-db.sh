#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER continuum_owner WITH PASSWORD 'continuum-test-password';
    CREATE DATABASE continuum OWNER continuum_owner;
    GRANT ALL PRIVILEGES ON DATABASE continuum TO continuum_owner;
EOSQL
