import { useState } from 'react';
import { motion } from 'framer-motion';
import type { KnimeWorkflowScheduleResponse } from '../types/api';
import { StatusBadge } from './StatusBadge';
import { Button } from './Button';

interface ScheduleCardProps {
  schedule: KnimeWorkflowScheduleResponse;
  workflowFileName: string | undefined;
  onPause: (scheduleId: string) => Promise<void>;
  onUnpause: (scheduleId: string) => Promise<void>;
  onTrigger: (scheduleId: string) => Promise<void>;
  onView: (schedule: KnimeWorkflowScheduleResponse) => void;
  onDelete: (schedule: KnimeWorkflowScheduleResponse) => void;
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function ScheduleCard({
  schedule,
  workflowFileName,
  onPause,
  onUnpause,
  onTrigger,
  onView,
  onDelete,
}: ScheduleCardProps) {
  const [loading, setLoading] = useState<string | null>(null);

  const handleAction = async (action: string, handler: () => Promise<void>) => {
    setLoading(action);
    try {
      await handler();
    } finally {
      setLoading(null);
    }
  };

  return (
    <motion.article
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
      whileHover={{ y: -4 }}
      className="group relative rounded-xl border border-divider bg-card p-5 transition-shadow hover:glow-accent"
    >
      {/* Header */}
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h3 className="text-lg font-semibold text-fg">{schedule.name}</h3>
          <p className="mt-1 text-xs text-fg-muted truncate max-w-[220px]" title={workflowFileName}>
            {workflowFileName ?? 'Unknown workflow'}
          </p>
        </div>
        <StatusBadge paused={schedule.paused} />
      </div>

      {/* Key Details */}
      <div className="mb-4 space-y-2 text-sm">
        <div className="flex items-center justify-between">
          <span className="text-fg-muted">Cron:</span>
          <code className="text-fg">{schedule.cronExpression}</code>
        </div>
        {schedule.timeZone && (
          <div className="flex items-center justify-between">
            <span className="text-fg-muted">Time zone:</span>
            <span className="text-fg">{schedule.timeZone}</span>
          </div>
        )}
        <div className="flex items-center justify-between">
          <span className="text-fg-muted">Next run:</span>
          <span className="text-fg">
            {schedule.nextRunTimes.length > 0 ? formatDateTime(schedule.nextRunTimes[0]) : '—'}
          </span>
        </div>
      </div>

      {/* Actions */}
      <div className="flex flex-wrap gap-2">
        <Button
          size="sm"
          variant="secondary"
          onClick={() => handleAction('trigger', () => onTrigger(schedule.scheduleId))}
          loading={loading === 'trigger'}
          disabled={loading !== null && loading !== 'trigger'}
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Trigger Now
        </Button>

        {schedule.paused ? (
          <Button
            size="sm"
            onClick={() => handleAction('unpause', () => onUnpause(schedule.scheduleId))}
            loading={loading === 'unpause'}
            disabled={loading !== null && loading !== 'unpause'}
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 3l14 9-14 9V3z" />
            </svg>
            Unpause
          </Button>
        ) : (
          <Button
            size="sm"
            variant="secondary"
            onClick={() => handleAction('pause', () => onPause(schedule.scheduleId))}
            loading={loading === 'pause'}
            disabled={loading !== null && loading !== 'pause'}
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Pause
          </Button>
        )}

        <Button size="sm" variant="ghost" onClick={() => onView(schedule)} disabled={loading !== null}>
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
          View
        </Button>

        <Button
          size="sm"
          variant="danger"
          onClick={() => onDelete(schedule)}
          disabled={loading !== null}
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
          Delete
        </Button>
      </div>
    </motion.article>
  );
}
