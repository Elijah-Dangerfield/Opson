-- Tie profiles to Supabase's auth.users: deleting a user cascades to their
-- profile, and a profile can't reference a non-existent user.
--
-- In production, auth.users is owned by Supabase Auth and already exists.
-- Locally and in tests it's a minimal stub created by init-auth.sql (see
-- apps/server/docker/init-auth.sql and src/test/resources/init-auth.sql).
ALTER TABLE profiles
    ADD CONSTRAINT profiles_user_id_fk
    FOREIGN KEY (user_id) REFERENCES auth.users (id) ON DELETE CASCADE;
