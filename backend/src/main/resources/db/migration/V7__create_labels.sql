CREATE TABLE labels (

    id UUID PRIMARY KEY
        DEFAULT gen_random_uuid(),

    name VARCHAR(100)
        NOT NULL,

    color VARCHAR(50)
        NOT NULL,

    user_id UUID NOT NULL,

    CONSTRAINT fk_labels_user
        FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE task_labels (

    task_id UUID NOT NULL,

    label_id UUID NOT NULL,

    PRIMARY KEY (
        task_id,
        label_id
    ),

    CONSTRAINT fk_task_labels_task
        FOREIGN KEY(task_id)
        REFERENCES tasks(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_task_labels_label
        FOREIGN KEY(label_id)
        REFERENCES labels(id)
        ON DELETE CASCADE
);