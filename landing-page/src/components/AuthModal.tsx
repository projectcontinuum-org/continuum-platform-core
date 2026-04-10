import { useState } from 'react';
import { motion } from 'framer-motion';

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  mode: 'signin' | 'signup';
  onSwitchMode: (mode: 'signin' | 'signup') => void;
  embedded?: boolean;
}

// Auth configuration - using server-side routes for OAuth flow
const AUTH_CONFIG = {
  // Server-side auth start endpoint - handles the OAuth2 flow with IdP hints
  authStartUrl: '/auth/start',
  // Direct IdP login endpoint (alternative approach)
  authIdpUrl: '/auth/idp',
};

// SSO Provider configurations
const SSO_PROVIDERS = [
  {
    id: 'google',
    name: 'Google',
    // The IdP alias configured in Keycloak
    keycloakIdpHint: 'google',
    icon: (
      <svg className="h-5 w-5" viewBox="0 0 24 24">
        <path
          fill="#4285F4"
          d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        />
        <path
          fill="#34A853"
          d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
        />
        <path
          fill="#FBBC05"
          d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
        />
        <path
          fill="#EA4335"
          d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
        />
      </svg>
    ),
    color: 'hover:bg-red-50 dark:hover:bg-red-900/10',
  },
  {
    id: 'github',
    name: 'GitHub',
    keycloakIdpHint: 'github',
    icon: (
      <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 24 24">
        <path
          fillRule="evenodd"
          d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
          clipRule="evenodd"
        />
      </svg>
    ),
    color: 'hover:bg-gray-100 dark:hover:bg-gray-800',
  },
  {
    id: 'microsoft',
    name: 'Microsoft',
    keycloakIdpHint: 'microsoft',
    icon: (
      <svg className="h-5 w-5" viewBox="0 0 24 24">
        <path fill="#F25022" d="M1 1h10v10H1z" />
        <path fill="#00A4EF" d="M1 13h10v10H1z" />
        <path fill="#7FBA00" d="M13 1h10v10H13z" />
        <path fill="#FFB900" d="M13 13h10v10H13z" />
      </svg>
    ),
    color: 'hover:bg-blue-50 dark:hover:bg-blue-900/10',
  },
  {
    id: 'linkedin',
    name: 'LinkedIn',
    keycloakIdpHint: 'linkedin',
    icon: (
      <svg className="h-5 w-5" fill="#0A66C2" viewBox="0 0 24 24">
        <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z" />
      </svg>
    ),
    color: 'hover:bg-blue-50 dark:hover:bg-blue-900/10',
  },
];

/**
 * Build the server-side auth URL with the IdP hint
 * The Express server will handle the OAuth2 flow with proper state management
 * @param idpHint - The Keycloak identity provider alias (e.g., 'google', 'github')
 */
function buildAuthUrl(idpHint?: string): string {
  if (idpHint) {
    // Use server-side route that handles the OAuth2 flow with IdP hints
    return `${AUTH_CONFIG.authStartUrl}?idp=${encodeURIComponent(idpHint)}`;
  }
  // No IdP hint - let Keycloak show its login page
  // This goes through the direct IdP endpoint for the default flow
  return `${AUTH_CONFIG.authIdpUrl}/keycloak`;
}

export default function AuthModal({ isOpen, onClose, mode, onSwitchMode, embedded = false }: AuthModalProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen && !embedded) return null;

  const handleSSOLogin = async (provider: typeof SSO_PROVIDERS[0]) => {
    setIsLoading(true);
    setError(null);

    try {
      // Build the auth URL with the IdP hint
      // The Express server will handle the OAuth2 flow
      const authUrl = buildAuthUrl(provider.keycloakIdpHint);

      console.log(`Initiating SSO login with ${provider.name}`, { authUrl });

      // Redirect to our Express server's auth endpoint
      // The server will handle the OAuth2 Proxy -> Keycloak flow with proper state
      window.location.href = authUrl;
    } catch (err) {
      setError('Failed to initiate sign-in. Please try again.');
      setIsLoading(false);
    }
  };

  // Handle sign in with email/password (Keycloak's built-in form)
  const handleEmailSignIn = () => {
    setIsLoading(true);
    // No IdP hint - redirect to server which will trigger Keycloak's login form
    window.location.href = buildAuthUrl();
  };

  // Embedded content (no modal wrapper)
  const content = (
    <div className={embedded ? '' : 'px-6 pb-8'}>
      {/* Error message */}
      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-500 text-sm">
          {error}
        </div>
      )}

      {/* SSO Buttons */}
      <div className="space-y-3">
        {SSO_PROVIDERS.map((provider) => (
          <button
            key={provider.id}
            onClick={() => handleSSOLogin(provider)}
            disabled={isLoading}
            className={`flex items-center justify-center gap-3 w-full px-4 py-3 rounded-lg border border-divider bg-base font-medium transition-all duration-200 ${provider.color} disabled:opacity-50 disabled:cursor-not-allowed hover:shadow-md`}
          >
            {provider.icon}
            <span>Continue with {provider.name}</span>
          </button>
        ))}
      </div>

      {/* Divider */}
      <div className="relative my-6">
        <div className="absolute inset-0 flex items-center">
          <div className="w-full border-t border-divider" />
        </div>
        <div className="relative flex justify-center text-sm">
          <span className="px-2 bg-card text-fg-muted">or</span>
        </div>
      </div>

      {/* Email/Password Sign In (Keycloak form) */}
      <button
        onClick={handleEmailSignIn}
        disabled={isLoading}
        className="flex items-center justify-center gap-3 w-full px-4 py-3 rounded-lg border border-divider bg-base font-medium transition-all duration-200 hover:bg-overlay/5 disabled:opacity-50 disabled:cursor-not-allowed hover:shadow-md"
      >
        <svg className="h-5 w-5 text-fg-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
        </svg>
        <span>Continue with Email</span>
      </button>

      {/* Loading overlay */}
      {isLoading && (
        <div className="mt-4 flex items-center justify-center gap-2 text-fg-muted">
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
          <span>Redirecting to sign in...</span>
        </div>
      )}

      {/* Switch mode link */}
      <p className="mt-6 text-center text-sm text-fg-muted">
        {mode === 'signup' ? (
          <>
            Already have an account?{' '}
            <button
              type="button"
              onClick={() => onSwitchMode('signin')}
              className="text-accent font-medium hover:underline"
            >
              Sign in
            </button>
          </>
        ) : (
          <>
            Don't have an account?{' '}
            <button
              type="button"
              onClick={() => onSwitchMode('signup')}
              className="text-accent font-medium hover:underline"
            >
              Sign up
            </button>
          </>
        )}
      </p>
    </div>
  );

  // If embedded, return content directly without modal wrapper
  if (embedded) {
    return content;
  }

  // Modal wrapper for non-embedded use
  return (
    <>
      {/* Backdrop */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        onClick={onClose}
        className="fixed inset-0 bg-black/50 backdrop-blur-sm z-40"
      />

      {/* Modal */}
      <div className="fixed inset-0 flex items-center justify-center z-50 p-4">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 20 }}
          transition={{ duration: 0.2 }}
          className="w-full max-w-md bg-card rounded-2xl border border-divider shadow-2xl overflow-hidden"
        >
          {/* Header */}
          <div className="relative px-6 pt-8 pb-6">
            <button
              onClick={onClose}
              className="absolute top-4 right-4 p-2 rounded-lg text-fg-muted hover:text-fg hover:bg-overlay/10 transition-colors"
              aria-label="Close modal"
            >
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            <div className="text-center">
              <div className="mx-auto h-12 w-12 rounded-full bg-accent/10 flex items-center justify-center mb-4">
                <img src="/Logo.png" alt="Continuum" className="h-8 w-8" />
              </div>
              <h2 className="text-2xl font-bold">
                {mode === 'signup' ? 'Create your account' : 'Welcome back'}
              </h2>
              <p className="mt-2 text-sm text-fg-muted">
                {mode === 'signup'
                  ? 'Get started with Continuum'
                  : 'Sign in to continue to Continuum'}
              </p>
            </div>
          </div>

          {content}
        </motion.div>
      </div>
    </>
  );
}
