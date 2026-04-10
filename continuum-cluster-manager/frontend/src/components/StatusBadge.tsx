import { motion } from 'framer-motion';
import type { WorkbenchStatus } from '../types/api';

interface StatusBadgeProps {
  status: WorkbenchStatus;
  showPulse?: boolean;
}

const STATUS_CONFIG: Record<WorkbenchStatus, { label: string; className: string }> = {
  RUNNING: { label: 'Running', className: 'status-running' },
  PENDING: { label: 'Pending', className: 'status-pending' },
  SUSPENDED: { label: 'Suspended', className: 'status-suspended' },
  FAILED: { label: 'Failed', className: 'status-failed' },
  TERMINATING: { label: 'Terminating', className: 'status-terminating' },
  DELETED: { label: 'Deleted', className: 'status-deleted' },
  UNKNOWN: { label: 'Unknown', className: 'status-unknown' },
};

export function StatusBadge({ status, showPulse = true }: StatusBadgeProps) {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.UNKNOWN;
  const isPulsing = showPulse && (status === 'PENDING' || status === 'TERMINATING');

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${config.className}`}>
      {isPulsing && (
        <motion.span
          className="h-2 w-2 rounded-full bg-current"
          animate={{ opacity: [1, 0.4, 1] }}
          transition={{ duration: 1.5, repeat: Infinity }}
        />
      )}
      {status === 'RUNNING' && (
        <span className="h-2 w-2 rounded-full bg-current" />
      )}
      {config.label}
    </span>
  );
}

