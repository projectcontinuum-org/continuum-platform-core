import { motion } from 'framer-motion';

export function LoadingState() {
  return (
    <div className="flex flex-col items-center justify-center py-16">
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
        className="mb-4"
      >
        <svg className="h-12 w-12" viewBox="0 0 48 48" fill="none">
          <circle
            cx="24"
            cy="24"
            r="20"
            stroke="rgb(var(--c-divider))"
            strokeWidth="4"
          />
          <motion.circle
            cx="24"
            cy="24"
            r="20"
            stroke="var(--svg-accent)"
            strokeWidth="4"
            strokeLinecap="round"
            strokeDasharray="80 120"
          />
        </svg>
      </motion.div>
      <p className="text-fg-muted">Loading workbenches...</p>
    </div>
  );
}

