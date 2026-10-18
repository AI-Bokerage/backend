-- Fix entry_type column from smallint to varchar when it was created with wrong type
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'billing_ledger_entries'
          AND column_name = 'entry_type'
          AND data_type <> 'character varying'
    ) THEN
        TRUNCATE TABLE billing_ledger_entries;
        ALTER TABLE billing_ledger_entries ALTER COLUMN entry_type TYPE VARCHAR(50) USING entry_type::text;
    END IF;
END $$;
