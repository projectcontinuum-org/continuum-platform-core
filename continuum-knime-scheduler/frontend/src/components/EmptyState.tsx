import { motion, useReducedMotion } from 'framer-motion';

interface EmptyStateProps {
  title: string;
  description: string;
  actionLabel: string;
  onActionClick: () => void;
}

export function EmptyState({ title, description, actionLabel, onActionClick }: EmptyStateProps) {
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
        <svg viewBox="0 0 120 120" className="h-32 w-32" fill="none">
          {/* Document */}
          <path
            d="M35 15 L75 15 L90 30 L90 105 L35 105 Z"
            stroke="var(--svg-accent)"
            strokeWidth="2"
            fill="rgb(var(--c-surface) / 0.5)"
          />
          <path d="M75 15 L75 30 L90 30" stroke="var(--svg-accent)" strokeWidth="2" fill="none" />

          {/* Lines */}
          <motion.line
            x1="45" y1="50" x2="80" y2="50"
            stroke="var(--svg-purple)"
            strokeWidth="2.5"
            strokeLinecap="round"
            animate={reducedMotion ? undefined : { opacity: [0.5, 1, 0.5] }}
            transition={{ duration: 2, repeat: Infinity }}
          />
          <line x1="45" y1="62" x2="80" y2="62" stroke="var(--svg-purple)" strokeWidth="2.5" strokeLinecap="round" opacity="0.6" />
          <line x1="45" y1="74" x2="65" y2="74" stroke="var(--svg-purple)" strokeWidth="2.5" strokeLinecap="round" opacity="0.4" />

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

      <h3 className="mb-2 text-xl font-semibold text-fg">{title}</h3>
      <p className="mb-6 max-w-md text-center text-fg-muted">{description}</p>

      <motion.button
        onClick={onActionClick}
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.98 }}
        className="inline-flex items-center gap-2 rounded-full bg-accent px-6 py-3 font-semibold text-on-accent transition-colors hover:bg-accent/90 focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-base"
      >
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
        </svg>
        {actionLabel}
      </motion.button>
    </motion.div>
  );
}
