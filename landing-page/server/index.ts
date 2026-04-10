import express, { Request, Response, NextFunction } from 'express';
import cookieParser from 'cookie-parser';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 8080;

// Configuration from environment variables
const config = {
  // Keycloak URLs
  keycloakUrl: process.env.KEYCLOAK_URL || 'https://keycloak.192.168.49.2.nip.io',
  keycloakRealm: process.env.KEYCLOAK_REALM || 'continuum',
  keycloakClientId: process.env.KEYCLOAK_CLIENT_ID || 'continuum',
  // Public URLs
  publicAuthUrl: process.env.PUBLIC_AUTH_URL || 'https://auth.192.168.49.2.nip.io',
  publicAppUrl: process.env.PUBLIC_APP_URL || 'https://continuum.192.168.49.2.nip.io',
  // Cookie domain (for cross-subdomain cookies)
  cookieDomain: process.env.COOKIE_DOMAIN || '.192.168.49.2.nip.io',
};

// Middleware
app.use(cookieParser());

// Serve static files from the dist directory
const distPath = path.join(__dirname, '..', 'dist');
app.use(express.static(distPath, {
  // Cache static assets
  maxAge: '1y',
  immutable: true,
}));

// ============================================================================
// Auth Flow Routes
// ============================================================================

/**
 * Start the OAuth2 authentication flow
 *
 * For IdP logins (Google, GitHub, etc.):
 * 1. Redirect directly to Keycloak with kc_idp_hint
 * 2. After Keycloak auth, redirect to a protected endpoint
 * 3. OAuth2 Proxy sees user is authenticated in Keycloak (SSO) and sets session
 *
 * For email login (no IdP hint):
 * 1. Redirect to OAuth2 Proxy which shows Keycloak's login page
 *
 * GET /auth/start?idp=google&rd=/dashboard
 *
 * Query params:
 *   - idp: (optional) Identity provider hint (google, github, microsoft, linkedin)
 *   - rd: (optional) URL to redirect to after successful auth (default: /)
 */
app.get('/auth/start', (req: Request, res: Response) => {
  const idpHint = req.query.idp as string | undefined;
  const redirectAfterAuth = req.query.rd as string || '/';

  // Store redirect URL in cookie for after auth completes
  res.cookie('_auth_redirect', redirectAfterAuth, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    maxAge: 10 * 60 * 1000, // 10 minutes
    sameSite: 'lax',
    path: '/',
    domain: config.cookieDomain,
  });

  if (idpHint) {
    // For IdP logins, redirect directly to Keycloak with kc_idp_hint
    // After Keycloak authenticates via the IdP, it will redirect to our callback
    // which then goes through OAuth2 Proxy to get the session cookie
    const keycloakAuthUrl = buildKeycloakDirectAuthUrl(idpHint);
    console.log(`[auth/start] IdP: ${idpHint}, Redirecting directly to Keycloak`);
    res.redirect(keycloakAuthUrl);
  } else {
    // For email login, go through OAuth2 Proxy which will show Keycloak's login page
    const oauth2StartUrl = `${config.publicAuthUrl}/oauth2/start?rd=${encodeURIComponent(config.publicAppUrl + '/auth/complete')}`;
    console.log(`[auth/start] No IdP, Redirecting to OAuth2 Proxy`);
    res.redirect(oauth2StartUrl);
  }
});

/**
 * Build Keycloak direct authorization URL with kc_idp_hint
 *
 * This redirects to Keycloak which then redirects to the IdP.
 * After IdP authentication, Keycloak redirects back to OUR callback,
 * not OAuth2 Proxy's callback.
 *
 * The redirect_uri points to our /auth/keycloak-callback endpoint.
 * From there, we'll trigger OAuth2 Proxy which will do instant SSO
 * since the user is already authenticated in Keycloak.
 */
function buildKeycloakDirectAuthUrl(idpHint: string): string {
  // Redirect back to our server, NOT OAuth2 Proxy
  // We'll handle establishing the OAuth2 Proxy session in the callback
  const redirectUri = `${config.publicAppUrl}/auth/keycloak-callback`;

  const params = new URLSearchParams({
    client_id: config.keycloakClientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'openid profile email',
    kc_idp_hint: idpHint,
  });

  return `${config.keycloakUrl}/realms/${config.keycloakRealm}/protocol/openid-connect/auth?${params.toString()}`;
}

/**
 * Keycloak callback - after direct Keycloak auth
 *
 * This is called after Keycloak authenticates the user via an IdP.
 * The user now has a valid Keycloak session.
 *
 * We redirect to OAuth2 Proxy's /oauth2/start endpoint.
 * Since the user is already authenticated in Keycloak (SSO),
 * OAuth2 Proxy will instantly get a code and set the session cookie.
 *
 * GET /auth/keycloak-callback?code=xxx&session_state=xxx
 */
app.get('/auth/keycloak-callback', (req: Request, res: Response) => {
  // Ignore the code - we don't process it ourselves
  // The user now has an active Keycloak session

  // Redirect to OAuth2 Proxy to establish its session
  // This will be instant because of SSO with Keycloak
  const oauth2StartUrl = `${config.publicAuthUrl}/oauth2/start?rd=${encodeURIComponent(config.publicAppUrl + '/auth/complete')}`;

  console.log('[auth/keycloak-callback] User authenticated in Keycloak, establishing OAuth2 Proxy session via SSO');
  res.redirect(oauth2StartUrl);
});

/**
 * Auth complete - final step after OAuth2 Proxy sets the session
 *
 * OAuth2 Proxy redirects here after successfully setting the session cookie.
 * We read the original redirect URL and send the user there.
 *
 * GET /auth/complete
 */
app.get('/auth/complete', (req: Request, res: Response) => {
  // Get the redirect URL from cookie (set during /auth/start)
  const redirectUrl = req.cookies?._auth_redirect || '/';

  // Clear the redirect cookie
  res.clearCookie('_auth_redirect', {
    path: '/',
    domain: config.cookieDomain,
  });

  console.log(`[auth/complete] Session established, redirecting to: ${redirectUrl}`);
  res.redirect(redirectUrl);
});

/**
 * Direct IdP login shortcut
 *
 * GET /auth/:provider
 * GET /auth/google
 * GET /auth/github
 */
app.get('/auth/:provider', (req: Request, res: Response) => {
  const provider = req.params.provider;
  const redirectAfterAuth = req.query.rd as string || '/';

  // Skip if it's a known route
  if (['start', 'complete', 'signout', 'keycloak-callback'].includes(provider)) {
    return res.status(404).json({ error: 'Not found' });
  }

  // Validate provider
  const validProviders = ['google', 'github', 'microsoft', 'linkedin', 'email'];
  if (!validProviders.includes(provider)) {
    return res.status(400).json({
      error: 'Invalid provider',
      validProviders
    });
  }

  // Store redirect URL
  res.cookie('_auth_redirect', redirectAfterAuth, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    maxAge: 10 * 60 * 1000,
    sameSite: 'lax',
    path: '/',
    domain: config.cookieDomain,
  });

  if (provider === 'email') {
    // For email login, go through OAuth2 Proxy
    const oauth2StartUrl = `${config.publicAuthUrl}/oauth2/start?rd=${encodeURIComponent(config.publicAppUrl + '/auth/complete')}`;
    console.log(`[auth/email] Redirecting to OAuth2 Proxy`);
    res.redirect(oauth2StartUrl);
  } else {
    // For IdP logins, redirect directly to Keycloak
    const keycloakAuthUrl = buildKeycloakDirectAuthUrl(provider);
    console.log(`[auth/${provider}] Redirecting directly to Keycloak`);
    res.redirect(keycloakAuthUrl);
  }
});

/**
 * Sign out - clear OAuth2 Proxy session and Keycloak session
 *
 * GET /auth/signout?rd=/
 */
app.get('/auth/signout', (req: Request, res: Response) => {
  const redirectAfterLogout = req.query.rd as string || '/';

  // Redirect to OAuth2 Proxy's sign out endpoint
  const oauth2SignoutUrl = `${config.publicAuthUrl}/oauth2/sign_out?rd=${encodeURIComponent(config.publicAppUrl + redirectAfterLogout)}`;

  console.log(`[auth/signout] Redirecting to OAuth2 Proxy signout`);
  res.redirect(oauth2SignoutUrl);
});

// ============================================================================
// Health check endpoint
// ============================================================================
app.get('/health', (_req: Request, res: Response) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// ============================================================================
// API endpoint to get auth configuration (for the frontend)
// ============================================================================
app.get('/api/auth/config', (_req: Request, res: Response) => {
  res.json({
    authStartUrl: '/auth/start',
    authSignoutUrl: '/auth/signout',
    providers: ['google', 'github', 'microsoft', 'linkedin'],
  });
});

// ============================================================================
// API endpoint to get current user info
// ============================================================================
// This endpoint proxies to OAuth2 Proxy's userinfo endpoint to get the
// authenticated user's information (name, email, picture, etc.)

interface OIDCUserInfo {
  email?: string;
  name?: string;
  picture?: string;
  preferred_username?: string;
  given_name?: string;
  family_name?: string;
  sub?: string;
}

app.get('/api/auth/userinfo', async (req: Request, res: Response) => {
  try {
    // Forward the request to OAuth2 Proxy's userinfo endpoint
    // OAuth2 Proxy will validate the session cookie and return user info
    const userinfoUrl = `${config.publicAuthUrl}/oauth2/userinfo`;

    // Forward cookies to OAuth2 Proxy
    const cookies = req.headers.cookie || '';

    const response = await fetch(userinfoUrl, {
      headers: {
        'Cookie': cookies,
      },
    });

    if (!response.ok) {
      // User is not authenticated
      return res.status(401).json({ authenticated: false });
    }

    const userinfo = await response.json() as OIDCUserInfo;

    // Return user info with authenticated flag
    res.json({
      authenticated: true,
      user: {
        email: userinfo.email,
        name: userinfo.name || userinfo.preferred_username || userinfo.email?.split('@')[0],
        picture: userinfo.picture,
        preferredUsername: userinfo.preferred_username,
        givenName: userinfo.given_name,
        familyName: userinfo.family_name,
      },
    });
  } catch (error) {
    console.error('[api/auth/userinfo] Error fetching user info:', error);
    res.status(401).json({ authenticated: false });
  }
});

// ============================================================================
// SPA Fallback - serve index.html for all other routes
// ============================================================================
app.get('*', (_req: Request, res: Response) => {
  res.sendFile(path.join(distPath, 'index.html'));
});

// Error handling middleware
app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error('Error:', err);
  res.status(500).json({ error: 'Internal Server Error' });
});

app.listen(PORT, () => {
  console.log(`🚀 Landing page server running on port ${PORT}`);
  console.log(`📋 Configuration:`);
  console.log(`   - Keycloak URL: ${config.keycloakUrl}`);
  console.log(`   - Keycloak Realm: ${config.keycloakRealm}`);
  console.log(`   - Public Auth URL: ${config.publicAuthUrl}`);
  console.log(`   - Public App URL: ${config.publicAppUrl}`);
  console.log(`   - Cookie Domain: ${config.cookieDomain}`);
});

export default app;









