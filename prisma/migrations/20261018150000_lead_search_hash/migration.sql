-- Smart search: fingerprint of the text last sent to search-svc.
ALTER TABLE "Lead" ADD COLUMN "searchHash" TEXT;
