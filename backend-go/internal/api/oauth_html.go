// Code generated from OAuthController.java HTML templates; DO NOT EDIT by hand.

package api

func renderAuthorizeFormHTML(actionUrl, clientName, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, errorMessage, currentEmail, cancelUrl string) string {
	errorBanner := ""
	if errorMessage != "" {
		errorBanner = `<div class="alert-error"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg><span>` +
			escapeHTML(errorMessage) +
			`</span></div>`
	}
	return `<!DOCTYPE html>
` +
		`<html lang="en">
` +
		`<head>
` +
		`  <meta charset="UTF-8">
` +
		`  <meta name="viewport" content="width=device-width, initial-scale=1.0">
` +
		`  <title>Authorize ` +
		escapeHTML(clientName) +
		` | TodoFlow</title>
` +
		`  <style>
` +
		`    :root { --primary: #3b82f6; --primary-hover: #2563eb; --bg: #0f172a; --card-bg: #1e293b; --text: #f8fafc; --text-muted: #94a3b8; --border: #334155; --danger: #ef4444; }
` +
		`    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: var(--bg); color: var(--text); display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 16px; box-sizing: border-box; }
` +
		`    .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 16px; padding: 32px; width: 100%; max-width: 440px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 8px 10px -6px rgba(0, 0, 0, 0.5); }
` +
		`    .header { text-align: center; margin-bottom: 24px; }
` +
		`    .logo { display: inline-flex; align-items: center; justify-content: center; width: 52px; height: 52px; border-radius: 12px; background: linear-gradient(135deg, #3b82f6, #6366f1); margin-bottom: 16px; box-shadow: 0 0 20px rgba(59, 130, 246, 0.4); }
` +
		`    .logo svg { width: 28px; height: 28px; color: white; }
` +
		`    h1 { font-size: 20px; font-weight: 700; margin: 0 0 8px; }
` +
		`    p.subtitle { color: var(--text-muted); font-size: 14px; margin: 0; line-height: 1.5; }
` +
		`    .permissions { background: #0f172a80; border: 1px solid var(--border); border-radius: 12px; padding: 14px; margin: 20px 0; }
` +
		`    .permissions-title { font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); font-weight: 600; margin-bottom: 8px; }
` +
		`    .perm-item { display: flex; align-items: center; gap: 10px; font-size: 13px; color: #e2e8f0; margin-bottom: 6px; }
` +
		`    .perm-item svg { color: #10b981; flex-shrink: 0; }
` +
		`    .form-group { margin-bottom: 16px; text-align: left; }
` +
		`    label { display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; color: #cbd5e1; }
` +
		`    input[type="email"], input[type="password"] { width: 100%; box-sizing: border-box; background: #0f172a; border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: var(--text); font-size: 14px; outline: none; transition: border-color 0.2s; }
` +
		`    input[type="email"]:focus, input[type="password"]:focus { border-color: var(--primary); box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2); }
` +
		`    .actions { display: flex; flex-direction: column; gap: 10px; margin-top: 24px; }
` +
		`    .btn-primary { background: var(--primary); color: white; border: none; border-radius: 8px; padding: 12px; font-size: 14px; font-weight: 600; cursor: pointer; transition: background 0.2s; width: 100%; }
` +
		`    .btn-primary:hover { background: var(--primary-hover); }
` +
		`    .btn-cancel { background: transparent; color: var(--text-muted); border: 1px solid var(--border); border-radius: 8px; padding: 10px; font-size: 14px; text-decoration: none; text-align: center; font-weight: 500; transition: all 0.2s; }
` +
		`    .btn-cancel:hover { background: #334155; color: var(--text); }
` +
		`    .alert-error { background: rgba(239, 68, 68, 0.15); border: 1px solid var(--danger); border-radius: 8px; padding: 10px 12px; font-size: 13px; color: #fca5a5; display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
` +
		`    .footer { text-align: center; margin-top: 20px; font-size: 11px; color: var(--text-muted); }
` +
		`  </style>
` +
		`</head>
` +
		`<body>
` +
		`  <div class="card">
` +
		`    <div class="header">
` +
		`      <div class="logo">
` +
		`        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
` +
		`      </div>
` +
		`      <h1>Authorize Connection</h1>
` +
		`      <p class="subtitle"><strong>` +
		escapeHTML(clientName) +
		`</strong> is requesting permission to access your TodoFlow account.</p>
` +
		`    </div>
` +
		`    <div class="permissions">
` +
		`      <div class="permissions-title">Permissions Granted</div>
` +
		`      <div class="perm-item"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg><span>Create and manage your tasks and checklists</span></div>
` +
		`      <div class="perm-item"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg><span>Organize tasks into projects and workspaces</span></div>
` +
		`    </div>
` +
		`    ` +
		errorBanner +
		`
` +
		`    <form method="POST" action="` +
		escapeHTML(actionUrl) +
		`">
` +
		`      <input type="hidden" name="client_id" value="` +
		escapeHTML(clientId) +
		`">
` +
		`      <input type="hidden" name="redirect_uri" value="` +
		escapeHTML(redirectUri) +
		`">
` +
		`      <input type="hidden" name="state" value="` +
		escapeHTML(state) +
		`">
` +
		`      <input type="hidden" name="scope" value="` +
		escapeHTML(scope) +
		`">
` +
		`      <input type="hidden" name="code_challenge" value="` +
		escapeHTML(codeChallenge) +
		`">
` +
		`      <input type="hidden" name="code_challenge_method" value="` +
		escapeHTML(codeChallengeMethod) +
		`">
` +
		`      <div class="form-group">
` +
		`        <label for="email">TodoFlow Account Email</label>
` +
		`        <input type="email" id="email" name="email" value="` +
		escapeHTML(currentEmail) +
		`" required autofocus placeholder="you@example.com">
` +
		`      </div>
` +
		`      <div class="form-group">
` +
		`        <label for="password">Password</label>
` +
		`        <input type="password" id="password" name="password" required placeholder="Enter your password">
` +
		`      </div>
` +
		`      <div class="actions">
` +
		`        <button type="submit" class="btn-primary">Authorize ` +
		escapeHTML(clientName) +
		`</button>
` +
		`        <a href="` +
		escapeHTML(cancelUrl) +
		`" class="btn-cancel">Cancel</a>
` +
		`      </div>
` +
		`    </form>
` +
		`    <div class="footer">TodoFlow OAuth 2.1 • Secured with PKCE</div>
` +
		`  </div>
` +
		`</body>
` +
		`</html>`
}

func renderErrorPageHTML(title, description string) string {
	return `<!DOCTYPE html>
` +
		`<html lang="en">
` +
		`<head>
` +
		`  <meta charset="UTF-8">
` +
		`  <title>` +
		escapeHTML(title) +
		` | TodoFlow</title>
` +
		`  <style>
` +
		`    body { margin: 0; font-family: sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; }
` +
		`    .card { background: #1e293b; border: 1px solid #ef4444; border-radius: 12px; padding: 28px; max-width: 400px; text-align: center; }
` +
		`    h2 { color: #ef4444; margin-top: 0; }
` +
		`    p { color: #94a3b8; font-size: 14px; }
` +
		`  </style>
` +
		`</head>
` +
		`<body>
` +
		`  <div class="card">
` +
		`    <h2>` +
		escapeHTML(title) +
		`</h2>
` +
		`    <p>` +
		escapeHTML(description) +
		`</p>
` +
		`  </div>
` +
		`</body>
` +
		`</html>`
}
