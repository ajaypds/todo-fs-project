-- Create oauth_clients table for OAuth 2.1 client registrations
CREATE TABLE oauth_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id VARCHAR(100) UNIQUE NOT NULL,
    client_secret VARCHAR(255),
    client_name VARCHAR(100) NOT NULL,
    redirect_uris TEXT NOT NULL,
    scopes VARCHAR(255) DEFAULT 'read write all',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create oauth_authorization_codes table for PKCE-secured authorization codes
CREATE TABLE oauth_authorization_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(128) UNIQUE NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id VARCHAR(100) NOT NULL,
    redirect_uri TEXT NOT NULL,
    scope VARCHAR(255),
    code_challenge VARCHAR(128),
    code_challenge_method VARCHAR(20),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_oauth_auth_codes_code ON oauth_authorization_codes(code);
CREATE INDEX idx_oauth_auth_codes_expires ON oauth_authorization_codes(expires_at);

-- Seed default client for ChatGPT Actions / Plugins
INSERT INTO oauth_clients (client_id, client_secret, client_name, redirect_uris, scopes)
VALUES (
    'chatgpt-plugin',
    'todoflow-chatgpt-secret-2026',
    'ChatGPT TodoFlow Plugin',
    'https://chat.openai.com/aip,https://chatgpt.com/aip,https://todoflow.ddns.net,http://localhost',
    'read write all'
) ON CONFLICT (client_id) DO NOTHING;
