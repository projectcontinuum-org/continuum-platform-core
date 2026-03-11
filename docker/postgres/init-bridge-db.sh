#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE continuum_bridge;
    GRANT ALL PRIVILEGES ON DATABASE continuum_bridge TO temporal;
EOSQL
