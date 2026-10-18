-- Runs only when the data volume is first created. POSTGRES_DB=crm is the CRM's own database.
CREATE DATABASE auth_db;
CREATE DATABASE tenant_db;
-- One per AI service (see AI-service/docker/initdb/01-databases.sql for why); pgvector is created
-- by search-svc's migration.
CREATE DATABASE broker_gateway;
CREATE DATABASE broker_voice;
CREATE DATABASE broker_docread;
CREATE DATABASE broker_notes;
CREATE DATABASE broker_email;
CREATE DATABASE broker_assistant;
CREATE DATABASE broker_search;
