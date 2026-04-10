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
          {/* Monitor */}
          <rect x="20" y="20" width="80" height="55" rx="4" stroke="var(--svg-accent)" strokeWidth="2" fill="rgb(var(--c-surface) / 0.5)" />
          <rect x="26" y="26" width="68" height="40" rx="2" fill="rgb(var(--c-bg))" />

          {/* Monitor stand */}
          <path d="M50 75 L50 85 L40 95 L80 95 L70 85 L70 75" stroke="var(--svg-accent)" strokeWidth="2" fill="none" />

          {/* Code lines on screen */}
          <motion.rect
            x="32" y="32" width="30" height="3" rx="1" fill="var(--svg-purple)"
            animate={reducedMotion ? undefined : { opacity: [0.5, 1, 0.5] }}
            transition={{ duration: 2, repeat: Infinity }}
          />
          <rect x="32" y="40" width="45" height="3" rx="1" fill="var(--svg-accent)" opacity="0.5" />
          <rect x="32" y="48" width="25" height="3" rx="1" fill="var(--svg-accent)" opacity="0.3" />
          <rect x="32" y="56" width="40" height="3" rx="1" fill="var(--svg-accent)" opacity="0.5" />

          {/* Plus sign */}
          <motion.g
            animate={reducedMotion ? undefined : { scale: [1, 1.1, 1] }}
            transition={{ duration: 2, repeat: Infinity }}
          >
            <circle cx="90" cy="90" r="18" fill="var(--svg-accent)" opacity="0.15" />
            <path d="M90 82 L90 98 M82 90 L98 90" stroke="var(--svg-accent)" strokeWidth="3" strokeLinecap="round" />
          </motion.g>
        </svg>
      </div>

      <h3 className="mb-2 text-xl font-semibold text-fg">No Workbenches Yet</h3>
      <p className="mb-6 max-w-md text-center text-fg-muted">
        Create your first Continuum workbench to start building visual workflows.
        Each workbench is an isolated environment with persistent storage.
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
        Create Your First Workbench
      </motion.button>
    </motion.div>
  );
}

