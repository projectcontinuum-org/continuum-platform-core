import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import type { WorkbenchResponse } from '../types/api';
import { useWorkbenchReadiness } from '../hooks/useWorkbenchReadiness';
import { StatusBadge } from './StatusBadge';
import { Button } from './Button';
import { Modal } from './Modal';

interface WorkbenchCardProps {
  workbench: WorkbenchResponse;
  onSuspend: (instanceName: string) => Promise<void>;
  onResume: (instanceName: string) => Promise<void>;
  onDelete: (instanceName: string) => Promise<void>;
  onOpen: (workbench: WorkbenchResponse) => void;
}

export function WorkbenchCard({ workbench, onSuspend, onResume, onDelete, onOpen }: WorkbenchCardProps) {
  const [loading, setLoading] = useState<string | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [expanded, setExpanded] = useState(false);

  const isRunning = workbench.status === 'RUNNING';
  const isSuspended = workbench.status === 'SUSPENDED';
  const isPending = workbench.status === 'PENDING';

  // Poll readiness only when the workbench is running
  const { ready, checking } = useWorkbenchReadiness(workbench.instanceName, isRunning);

  const canSuspend = isRunning;
  const canResume = isSuspended;
  const canDelete = !isPending;
  const canOpen = isRunning && ready;

  const handleAction = async (action: string, handler: () => Promise<void>) => {
    setLoading(action);
    try {
      await handler();
    } finally {
      setLoading(null);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };


  return (
    <>
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
            <h3 className="text-lg font-semibold text-fg">{workbench.instanceName}</h3>
            <p className="mt-1 text-xs text-fg-muted">
              Created {formatDate(workbench.createdAt)}
            </p>
          </div>
          <StatusBadge status={workbench.status} />
        </div>

        {/* Key Details */}
        <div className="mb-4 space-y-2 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-fg-muted">Image:</span>
            <span className="truncate max-w-[200px] text-fg" title={workbench.image}>
              {workbench.image.includes(':') ? workbench.image.split(':').pop() : workbench.image.split('/').pop()}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-fg-muted">Resources:</span>
            <span className="text-fg">
              {workbench.resources.cpuRequest} CPU, {workbench.resources.memoryRequest} RAM
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-fg-muted">Storage:</span>
            <span className="text-fg">{workbench.resources.storageSize}</span>
          </div>
        </div>

        {/* Expandable Details */}
        <button
          type="button"
          onClick={() => setExpanded(!expanded)}
          className="mb-3 flex w-full items-center gap-1.5 text-xs text-fg-muted hover:text-accent transition-colors"
        >
          <svg
            className={`h-3.5 w-3.5 transition-transform ${expanded ? 'rotate-90' : ''}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
          {expanded ? 'Hide details' : 'More details'}
        </button>

        <AnimatePresence initial={false}>
          {expanded && (
            <motion.div
              key="details"
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.2, ease: 'easeInOut' }}
              className="overflow-hidden"
            >
              <div className="mb-4 space-y-2 rounded-lg bg-surface/50 p-3 text-xs">
            <div className="flex items-center justify-between">
              <span className="text-fg-muted">Instance ID:</span>
              <code className="text-fg select-all" title={workbench.instanceId}>
                {workbench.instanceId}
              </code>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-fg-muted">Namespace:</span>
              <span className="text-fg">{workbench.namespace}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-fg-muted">CPU Limit:</span>
              <span className="text-fg">{workbench.resources.cpuLimit}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-fg-muted">Memory Limit:</span>
              <span className="text-fg">{workbench.resources.memoryLimit}</span>
            </div>
            {workbench.serviceEndpoint && (
              <div>
                <span className="text-fg-muted">Service Endpoint:</span>
                <code className="mt-1 block break-all text-accent">{workbench.serviceEndpoint}</code>
              </div>
            )}
            <div className="flex items-center justify-between">
              <span className="text-fg-muted">Updated:</span>
              <span className="text-fg">{formatDate(workbench.updatedAt)}</span>
            </div>
            </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Actions */}
        <div className="flex flex-wrap gap-2">
          {isRunning && (
            <Button
              size="sm"
              onClick={() => onOpen(workbench)}
              disabled={!canOpen || loading !== null}
              loading={checking}
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
              </svg>
              {checking ? 'Starting…' : 'Open'}
            </Button>
          )}

          {canSuspend && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => handleAction('suspend', () => onSuspend(workbench.instanceName))}
              loading={loading === 'suspend'}
              disabled={loading !== null && loading !== 'suspend'}
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              Suspend
            </Button>
          )}

          {canResume && (
            <Button
              size="sm"
              onClick={() => handleAction('resume', () => onResume(workbench.instanceName))}
              loading={loading === 'resume'}
              disabled={loading !== null && loading !== 'resume'}
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              Resume
            </Button>
          )}

          {canDelete && (
            <Button
              size="sm"
              variant="danger"
              onClick={() => setShowDeleteConfirm(true)}
              disabled={loading !== null}
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              Delete
            </Button>
          )}
        </div>
      </motion.article>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={showDeleteConfirm}
        onClose={() => { setShowDeleteConfirm(false); setDeleteConfirmText(''); }}
        title="Delete Workbench"
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-fg-muted">
            Are you sure you want to delete <span className="font-semibold text-fg">{workbench.instanceName}</span>?
            This action cannot be undone and all data will be permanently lost.
          </p>
          <div>
            <label className="block text-sm text-fg-muted mb-1.5">
              Type <span className="font-semibold text-fg">{workbench.instanceName}</span> to confirm
            </label>
            <input
              type="text"
              value={deleteConfirmText}
              onChange={(e) => setDeleteConfirmText(e.target.value)}
              placeholder={workbench.instanceName}
              className="w-full rounded-lg border border-divider bg-surface px-3 py-2 text-sm text-fg placeholder:text-fg-muted/40 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              autoFocus
            />
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={() => { setShowDeleteConfirm(false); setDeleteConfirmText(''); }}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={loading === 'delete'}
              disabled={deleteConfirmText !== workbench.instanceName}
              onClick={async () => {
                await handleAction('delete', () => onDelete(workbench.instanceName));
                setShowDeleteConfirm(false);
                setDeleteConfirmText('');
              }}
            >
              Delete
            </Button>
          </div>
        </div>
      </Modal>
    </>
  );
}
