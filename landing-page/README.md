# Continuum Platform Landing Page

A sign-in page for the Continuum Platform with SSO authentication support, powered by Express.js and React.

## Features

- 🔐 **SSO Authentication** - Support for Google, GitHub, Microsoft, and LinkedIn OAuth via Keycloak
- 🌓 **Dark/Light Mode** - Automatic theme detection with manual toggle
- 📱 **Fully Responsive** - Optimized for all screen sizes
- ⚡ **Express.js Backend** - Handles OAuth2 flow with IdP hints for direct identity provider login
- 🎨 **Continuum Branding** - Consistent with the platform's visual identity

## Architecture

The landing page uses an Express.js server to:
1. Serve the static React frontend
2. Handle the OAuth2 authentication flow with OAuth2 Proxy and Keycloak
3. Use direct Keycloak redirects with `kc_idp_hint` for IdP selection

### Auth Flow (with IdP selection)

```
User clicks "Sign in with Google"
    ↓
GET /auth/start?idp=google (Express server)
    ↓ sets _auth_redirect cookie
Redirect directly to Keycloak with kc_idp_hint=google
    ↓ Keycloak skips login page
User authenticates with Google
    ↓
Google redirects to Keycloak
    ↓
Keycloak redirects to Landing Page /auth/keycloak-callback
    ↓ User now has Keycloak session (but no OAuth2 Proxy session yet)
Redirect to OAuth2 Proxy /oauth2/start
    ↓ OAuth2 Proxy redirects to Keycloak
    ↓ Keycloak sees user is already authenticated (SSO)
    ↓ Keycloak immediately returns auth code
OAuth2 Proxy validates code, sets session cookie
    ↓
OAuth2 Proxy redirects to Landing Page /auth/complete
    ↓ reads _auth_redirect cookie
Redirect to originally requested URL
    ↓
User is authenticated ✅
```

### Auth Flow (email/password login)

```
User clicks "Sign in with Email"
    ↓
GET /auth/start (no idp param)
    ↓
Redirect to OAuth2 Proxy /oauth2/start
    ↓
OAuth2 Proxy redirects to Keycloak login page
    ↓
User enters credentials
    ↓
(standard OAuth2 flow continues...)
```

### Keycloak Client Configuration

The Keycloak client must have these redirect URIs configured:

- `https://auth.<domain>/oauth2/callback` - OAuth2 Proxy callback
- `https://continuum.<domain>/auth/keycloak-callback` - Landing page callback (for direct IdP flows)

## Tech Stack

- **Express.js** - Backend server for auth flow handling
- **React 18** - Frontend UI library
- **TypeScript** - Type safety
- **Tailwind CSS** - Utility-first CSS framework
- **Framer Motion** - Smooth animations
- **Vite** - Build tool and dev server

## Getting Started

### Prerequisites

- Node.js 18+ 
- npm or yarn

### Installation

```bash
# Navigate to the landing-page directory
cd continuum-platform-core/landing-page

# Install dependencies
npm install

# Start development server (frontend only)
npm run dev

# Start Express server in development
npm run dev:server
```

The Vite dev server runs at `http://localhost:3000`.
The Express server runs at `http://localhost:8080`.

### Building for Production

```bash
# Build both frontend and server
npm run build:all

# Start production server
npm start
```

## Project Structure

```
landing-page/
├── public/
│   └── Logo.png                # Continuum logo
├── src/
│   ├── components/
│   │   └── AuthModal.tsx       # SSO authentication component
│   ├── App.tsx                 # Main app component
│   ├── index.css               # Global styles & theme
│   └── main.tsx                # Entry point
├── server/
│   ├── index.ts                # Express server with auth routes
│   └── tsconfig.json           # Server TypeScript config
├── dist/                       # Built frontend (Vite output)
├── dist-server/                # Built server (TypeScript output)
├── Dockerfile                  # Multi-stage Docker build
├── package.json
├── tailwind.config.js
├── tsconfig.json
└── vite.config.ts
```

## Environment Variables

The Express server accepts the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | Server port | `8080` |
| `NODE_ENV` | Environment mode | `development` |
| `KEYCLOAK_URL` | Keycloak base URL | `https://keycloak.192.168.49.2.nip.io` |
| `KEYCLOAK_REALM` | Keycloak realm name | `continuum` |
| `KEYCLOAK_CLIENT_ID` | OAuth2 client ID | `continuum` |
| `PUBLIC_AUTH_URL` | Public OAuth2 Proxy URL | `https://auth.192.168.49.2.nip.io` |
| `PUBLIC_APP_URL` | Public landing page URL | `https://continuum.192.168.49.2.nip.io` |
| `COOKIE_DOMAIN` | Cookie domain for cross-subdomain auth | `.192.168.49.2.nip.io` |

## API Endpoints

### Auth Routes

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/auth/start?idp=<provider>&rd=<url>` | GET | Start OAuth flow with optional IdP hint |
| `/auth/<provider>?rd=<url>` | GET | Direct IdP login shortcut |
| `/auth/keycloak-callback` | GET | Callback from direct Keycloak auth |
| `/auth/complete` | GET | Final callback after OAuth2 Proxy session established |
| `/auth/signout?rd=<url>` | GET | Sign out and clear session |

### Utility Routes

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check endpoint |
| `/api/auth/config` | GET | Get auth configuration for frontend |

## SSO Configuration

The landing page includes SSO buttons for:
- Google
- GitHub  
- Microsoft
- LinkedIn

### Keycloak Setup

1. Create identity providers in Keycloak for each SSO provider
2. Use the provider alias (e.g., `google`, `github`) as the `kc_idp_hint`
3. Configure the OAuth2 client with proper redirect URIs

## Theming

The landing page uses CSS custom properties for theming, matching the Continuum brand colors.

### Light Theme
```css
:root {
  --c-bg: 245 245 245;
  --c-accent: 112 86 151;
  /* ... */
}
```

### Dark Theme
```css
.dark {
  --c-bg: 41 45 62;
  --c-accent: 196 168 255;
  /* ... */
}
```

## Adding New SSO Providers

1. Add the provider to Keycloak as an identity provider
2. Edit `src/components/AuthModal.tsx` and add a new entry to `SSO_PROVIDERS`:

```typescript
{
  id: 'provider-id',
  name: 'Provider Name',
  keycloakIdpHint: 'keycloak-idp-alias',
  icon: <YourIcon />,
  color: 'hover:bg-color-50 dark:hover:bg-color-900/10',
}
```

## Docker

Build and run the Docker image:

```bash
# Build
docker build -t landing-page .

# Run
docker run -p 8080:8080 \
  -e KEYCLOAK_URL=https://keycloak.example.com \
  -e PUBLIC_AUTH_URL=https://auth.example.com \
  -e PUBLIC_APP_URL=https://app.example.com \
  landing-page
```

## License

Apache 2.0 - See the main repository LICENSE file.
