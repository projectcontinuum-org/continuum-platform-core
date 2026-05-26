import { motion, useReducedMotion } from 'framer-motion';

export function EmptyState({ onCreateClick }: { onCreateClick: () => void }) {
  const reducedMotion = useReducedMotion() ?? false;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: reducedMotion ? 0 : 0.5 }}
      className="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-divider bg-surface/30 px-6 py-16"
    >
      {/* Illustration */}
      <div className="mb-6">
        <svg
          viewBox="0 0 120 120"
          className="h-32 w-32"
          fill="none"
        >
          {/* Shield */}
          <path
            d="M60 10 L95 30 L95 60 C95 85 75 105 60 110 C45 105 25 85 25 60 L25 30 Z"
            stroke="var(--svg-accent)"
            strokeWidth="2"
            fill="rgb(var(--c-surface) / 0.5)"
          />

          {/* Key icon inside shield */}
          <motion.circle
            cx="55" cy="52" r="10"
            stroke="var(--svg-purple)"
            strokeWidth="2.5"
            fill="none"
            animate={reducedMotion ? undefined : { opacity: [0.5, 1, 0.5] }}
            transition={{ duration: 2, repeat: Infinity }}
          />
          <line x1="65" y1="52" x2="80" y2="52" stroke="var(--svg-purple)" strokeWidth="2.5" strokeLinecap="round" />
          <line x1="75" y1="52" x2="75" y2="60" stroke="var(--svg-purple)" strokeWidth="2.5" strokeLinecap="round" />
          <line x1="80" y1="52" x2="80" y2="58" stroke="var(--svg-purple)" strokeWidth="2.5" strokeLinecap="round" />

          {/* Lock hasp */}
          <rect x="48" y="68" width="24" height="18" rx="3" stroke="var(--svg-accent)" strokeWidth="2" fill="rgb(var(--c-bg))" />
          <path d="M53 68 L53 62 C53 56 67 56 67 62 L67 68" stroke="var(--svg-accent)" strokeWidth="2" fill="none" />
          <circle cx="60" cy="78" r="2" fill="var(--svg-accent)" />

          {/* Plus sign */}
          <motion.g
            animate={reducedMotion ? undefined : { scale: [1, 1.1, 1] }}
            transition={{ duration: 2, repeat: Infinity }}
          >
            <circle cx="95" cy="95" r="15" fill="var(--svg-accent)" opacity="0.15" />
            <path d="M95 88 L95 102 M88 95 L102 95" stroke="var(--svg-accent)" strokeWidth="3" strokeLinecap="round" />
          </motion.g>
        </svg>
      </div>

      <h3 className="mb-2 text-xl font-semibold text-fg">No Credentials Yet</h3>
      <p className="mb-6 max-w-md text-center text-fg-muted">
        Create your first credential to securely store and manage access to external services.
        All credentials are encrypted at rest and access-controlled per user.
      </p>

      <motion.button
        onClick={onCreateClick}
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.98 }}
        className="inline-flex items-center gap-2 rounded-full bg-accent px-6 py-3 font-semibold text-on-accent transition-colors hover:bg-accent/90 focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-base"
      >
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
        </svg>
        Create Your First Credential
      </motion.button>
    </motion.div>
  );
}
