CREATE TABLE task_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL,
    user_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_task_comments_task FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_comments_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_comments_task_id ON task_comments(task_id);
CREATE INDEX idx_task_comments_created_at ON task_comments(created_at);

ALTER TABLE tasks ADD COLUMN assigned_to UUID;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_assigned_to FOREIGN KEY(assigned_to) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_tasks_assigned_to ON tasks(assigned_to);

ALTER TABLE activities ADD COLUMN task_id UUID;
ALTER TABLE activities ADD CONSTRAINT fk_activities_task FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE;
CREATE INDEX idx_activities_task_id ON activities(task_id);
