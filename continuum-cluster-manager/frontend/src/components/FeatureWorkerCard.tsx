import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import type { FeatureWorkerResponse } from '../types/api';
import { StatusBadge } from './StatusBadge';
import { Button } from './Button';
import { Modal } from './Modal';

interface FeatureWorkerCardProps {
  featureWorker: FeatureWorkerResponse;
  onDelete: (workerName: string) => Promise<void>;
}

export function FeatureWorkerCard({ featureWorker, onDelete }: FeatureWorkerCardProps) {
  const [loading, setLoading] = useState<string | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [expanded, setExpanded] = useState(false);

  const isPending = featureWorker.status === 'PENDING';
  const canDelete = !isPending;

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

  const envVarEntries = Object.entries(featureWorker.envVars);

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
            <h3 className="text-lg font-semibold text-fg">{featureWorker.workerName}</h3>
            <p className="mt-1 text-xs text-fg-muted">
              Created {formatDate(featureWorker.createdAt)}
            </p>
          </div>
          <StatusBadge status={featureWorker.status} />
        </div>

        {/* Key Details */}
        <div className="mb-4 space-y-2 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-fg-muted">Image:</span>
            <span className="truncate max-w-[200px] text-fg" title={featureWorker.image}>
              {featureWorker.image.includes(':') ? featureWorker.image.split(':').pop() : featureWorker.image.split('/').pop()}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-fg-muted">Task Queue:</span>
            <code className="truncate max-w-[200px] text-fg text-xs" title={featureWorker.taskQueue}>
              {featureWorker.taskQueue}
            </code>
          </div>
          {featureWorker.overlayVariant && (
            <div className="flex items-center justify-between">
              <span className="text-fg-muted">Variant:</span>
              <span className="text-fg">{featureWorker.overlayVariant}</span>
            </div>
          )}
          <div className="flex items-center justify-between">
            <span className="text-fg-muted">Replicas:</span>
            <span className="text-fg">
              {featureWorker.autoscaling.enabled
                ? `${featureWorker.autoscaling.minReplicas}–${featureWorker.autoscaling.maxReplicas} (auto)`
                : featureWorker.replicas}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-fg-muted">Resources:</span>
            <span className="text-fg">
              {featureWorker.resources.cpuRequest} CPU, {featureWorker.resources.memoryRequest} RAM
            </span>
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
                  <span className="text-fg-muted">Worker ID:</span>
                  <code className="text-fg select-all" title={featureWorker.workerId}>
                    {featureWorker.workerId}
                  </code>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">Namespace:</span>
                  <span className="text-fg">{featureWorker.namespace}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">CPU Limit:</span>
                  <span className="text-fg">{featureWorker.resources.cpuLimit}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">Memory Limit:</span>
                  <span className="text-fg">{featureWorker.resources.memoryLimit}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">PVC Enabled:</span>
                  <span className="text-fg">{featureWorker.pvcEnabled ? 'Yes' : 'No'}</span>
                </div>
                {envVarEntries.length > 0 && (
                  <div>
                    <span className="text-fg-muted">Env Vars:</span>
                    <div className="mt-1 space-y-1">
                      {envVarEntries.map(([k, v]) => (
                        <div key={k} className="flex items-center justify-between gap-2">
                          <code className="text-fg truncate">{k}</code>
                          <code className="text-fg-muted truncate">{v}</code>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">Created By:</span>
                  <span className="text-fg">{featureWorker.createdBy}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">Updated:</span>
                  <span className="text-fg">{formatDate(featureWorker.updatedAt)}</span>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Actions */}
        <div className="flex flex-wrap gap-2">
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
        title="Delete Feature Worker"
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-fg-muted">
            Are you sure you want to delete <span className="font-semibold text-fg">{featureWorker.workerName}</span>?
            This action cannot be undone.
          </p>
          <div>
            <label className="block text-sm text-fg-muted mb-1.5">
              Type <span className="font-semibold text-fg">{featureWorker.workerName}</span> to confirm
            </label>
            <input
              type="text"
              value={deleteConfirmText}
              onChange={(e) => setDeleteConfirmText(e.target.value)}
              placeholder={featureWorker.workerName}
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
              disabled={deleteConfirmText !== featureWorker.workerName}
              onClick={async () => {
                await handleAction('delete', () => onDelete(featureWorker.workerName));
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
