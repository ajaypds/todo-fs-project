ALTER TABLE activities

ADD COLUMN user_id UUID;

UPDATE activities
SET user_id = (
    SELECT id
    FROM users
    LIMIT 1
);

ALTER TABLE activities

ALTER COLUMN user_id
SET NOT NULL;

ALTER TABLE activities

ADD CONSTRAINT fk_activities_user

FOREIGN KEY(user_id)

REFERENCES users(id)

ON DELETE CASCADE;