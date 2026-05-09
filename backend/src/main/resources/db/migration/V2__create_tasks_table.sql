CREATE TABLE tasks (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,

    title VARCHAR(500) NOT NULL,

    description TEXT,

    completed BOOLEAN NOT NULL DEFAULT FALSE,

    priority INTEGER NOT NULL DEFAULT 4,

    due_date TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_tasks_user
        FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);