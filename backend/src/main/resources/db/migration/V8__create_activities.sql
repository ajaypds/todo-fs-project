CREATE TABLE activities (

    id UUID PRIMARY KEY
        DEFAULT gen_random_uuid(),

    type VARCHAR(100)
        NOT NULL,

    message TEXT
        NOT NULL,

    created_at TIMESTAMP
        NOT NULL
);