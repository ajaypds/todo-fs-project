CREATE TABLE refresh_tokens (

    id UUID PRIMARY KEY
        DEFAULT gen_random_uuid(),

    token VARCHAR(500)
        NOT NULL UNIQUE,

    user_id UUID NOT NULL,

    expires_at TIMESTAMP NOT NULL,

    revoked BOOLEAN NOT NULL
        DEFAULT FALSE,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);