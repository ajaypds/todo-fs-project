CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workspace_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_workspace_member_user UNIQUE (workspace_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_workspaces_owner_id ON workspaces(owner_id);
CREATE INDEX IF NOT EXISTS idx_workspace_members_user ON workspace_members(user_id);
CREATE INDEX IF NOT EXISTS idx_workspace_members_workspace ON workspace_members(workspace_id);

-- Optional association on projects
ALTER TABLE projects ADD COLUMN IF NOT EXISTS workspace_id UUID REFERENCES workspaces(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_projects_workspace ON projects(workspace_id);

-- Backfill: create a default personal workspace for every existing user who does not have one
INSERT INTO workspaces (id, name, owner_id, created_at)
SELECT gen_random_uuid(), COALESCE(NULLIF(full_name, ''), 'Personal') || '''s Workspace', id, NOW()
FROM users
WHERE id NOT IN (SELECT owner_id FROM workspaces);

-- Backfill: make every workspace owner an OWNER member of their workspace
INSERT INTO workspace_members (id, workspace_id, user_id, role, joined_at)
SELECT gen_random_uuid(), w.id, w.owner_id, 'OWNER', NOW()
FROM workspaces w
WHERE NOT EXISTS (
    SELECT 1 FROM workspace_members wm WHERE wm.workspace_id = w.id AND wm.user_id = w.owner_id
);

-- Backfill: associate existing projects with owner's workspace
UPDATE projects p
SET workspace_id = w.id
FROM workspaces w
WHERE w.owner_id = p.user_id AND p.workspace_id IS NULL;
