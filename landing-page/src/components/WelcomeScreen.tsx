import { motion } from 'framer-motion';

export interface UserInfo {
  email: string;
  name: string;
  picture?: string;
  preferredUsername?: string;
  givenName?: string;
  familyName?: string;
}

interface WelcomeScreenProps {
  user: UserInfo;
  onSignOut: () => void;
}

export default function WelcomeScreen({ user, onSignOut }: WelcomeScreenProps) {
  // Get initials for fallback avatar
  const getInitials = (name: string) => {
    const parts = name.split(' ');
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  };

  const initials = getInitials(user.name || user.email);

  return (
    <div className="text-center">
      {/* Profile Picture */}
      <motion.div
        className="mx-auto mb-6"
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.5, type: 'spring' }}
      >
        {user.picture ? (
          <img
            src={user.picture}
            alt={user.name}
            className="w-24 h-24 rounded-full border-4 border-accent/20 shadow-lg mx-auto"
            referrerPolicy="no-referrer"
          />
        ) : (
          <div className="w-24 h-24 rounded-full bg-gradient-to-br from-accent to-purple flex items-center justify-center text-white text-2xl font-bold shadow-lg mx-auto">
            {initials}
          </div>
        )}
      </motion.div>

      {/* Welcome Message */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.1 }}
      >
        <h2 className="text-2xl font-bold mb-2">
          Welcome back, <span className="text-gradient">{user.givenName || user.name.split(' ')[0]}!</span>
        </h2>
        <p className="text-fg-muted mb-6">{user.email}</p>
      </motion.div>

      {/* Action Buttons */}
      <motion.div
        className="space-y-3"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.2 }}
      >
        {/* Open Workbench Button */}
        <a
          href="/workbench"
          className="flex items-center justify-center gap-3 w-full px-4 py-3 rounded-lg bg-accent text-white font-medium transition-all duration-200 hover:bg-accent/90 hover:shadow-lg hover:shadow-accent/25"
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z" />
          </svg>
          <span>Open Workbench</span>
        </a>

        {/* View Dashboard Button */}
        <a
          href="/dashboard"
          className="flex items-center justify-center gap-3 w-full px-4 py-3 rounded-lg border border-divider bg-base font-medium transition-all duration-200 hover:bg-overlay/5 hover:shadow-md"
        >
          <svg className="h-5 w-5 text-fg-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z" />
          </svg>
          <span>View Dashboard</span>
        </a>

        {/* Divider */}
        <div className="relative my-4">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-divider" />
          </div>
        </div>

        {/* Sign Out Button */}
        <button
          onClick={onSignOut}
          className="flex items-center justify-center gap-2 w-full px-4 py-2 rounded-lg text-fg-muted font-medium transition-all duration-200 hover:text-fg hover:bg-overlay/5"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          <span>Sign out</span>
        </button>
      </motion.div>
    </div>
  );
}

