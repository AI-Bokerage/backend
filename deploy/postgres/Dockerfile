# One Postgres (with pgvector, for AI search) holding every database of the CRM, created on first
# start. Used on Railway, which can't mount init scripts into an image service.
FROM pgvector/pgvector:pg16
COPY deploy/postgres/01-databases.sql /docker-entrypoint-initdb.d/
