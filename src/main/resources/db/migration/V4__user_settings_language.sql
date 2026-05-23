ALTER TABLE user_settings
    ADD COLUMN language TEXT;

ALTER TABLE user_settings
    ALTER COLUMN timezone DROP NOT NULL;
