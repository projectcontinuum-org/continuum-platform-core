import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Modal } from './Modal';
import { TypeBadge } from './TypeBadge';
import type { CredentialResponse } from '../types/api';

interface ViewCredentialModalProps {
  credential: CredentialResponse | null;
  isOpen: boolean;
  onClose: () => void;
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      const textarea = document.createElement('textarea');
      textarea.value = value;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <button
      type="button"
      onClick={handleCopy}
      className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md transition-colors ${
        copied
          ? 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400'
          : 'text-fg-muted hover:bg-surface hover:text-fg'
      }`}
      title={copied ? 'Copied!' : 'Copy to clipboard'}
    >
      <AnimatePresence mode="wait" initial={false}>
        {copied ? (
          <motion.svg
            key="check"
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            exit={{ scale: 0 }}
            transition={{ duration: 0.15 }}
            className="h-3.5 w-3.5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2.5}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
          </motion.svg>
        ) : (
          <motion.svg
            key="copy"
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            exit={{ scale: 0 }}
            transition={{ duration: 0.15 }}
            className="h-3.5 w-3.5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
          </motion.svg>
        )}
      </AnimatePresence>
    </button>
  );
}

function VisibilityToggle({ visible, onToggle }: { visible: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-fg-muted transition-colors hover:bg-surface hover:text-fg"
      title={visible ? 'Hide value' : 'Show value'}
    >
      {visible ? (
        <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
        </svg>
      ) : (
        <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
        </svg>
      )}
    </button>
  );
}

function SecretValue({ value }: { value: string }) {
  const [visible, setVisible] = useState(false);

  return (
    <>
      <code className="min-w-0 flex-1 break-all rounded bg-surface/50 px-2 py-1 font-mono text-sm text-fg select-none">
        {visible ? value : '\u2022'.repeat(Math.min(value.length, 24))}
      </code>
      <VisibilityToggle visible={visible} onToggle={() => setVisible(!visible)} />
      <CopyButton value={value} />
    </>
  );
}

function formatDateTime(iso: string | null): string {
  if (!iso) return 'Never';
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function ViewCredentialModal({ credential, isOpen, onClose }: ViewCredentialModalProps) {
  if (!credential) return null;

  const entries = Object.entries(credential.data);

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="View Credential" size="lg">
      <div className="space-y-5">
        {/* Credential Info */}
        <div className="flex flex-wrap items-center gap-3">
          <h3 className="text-lg font-semibold text-fg">{credential.name}</h3>
          <TypeBadge type={credential.type} />
        </div>

        {credential.description && (
          <p className="text-sm text-fg-muted">{credential.description}</p>
        )}

        {/* Key-Value Pairs */}
        <div>
          <h4 className="mb-2 text-xs font-semibold uppercase tracking-wider text-fg-muted">
            Credential Data
          </h4>
          <div className="overflow-hidden rounded-lg border border-divider">
            {entries.length > 0 ? (
              <div className="divide-y divide-divider/50">
                {entries.map(([key, value]) => (
                  <div
                    key={key}
                    className="flex items-center gap-2 px-4 py-3 transition-colors hover:bg-surface/30"
                  >
                    <span className="w-1/3 shrink-0 text-sm font-medium text-fg-muted">
                      {key}
                    </span>
                    <SecretValue value={value} />
                  </div>
                ))}
              </div>
            ) : (
              <p className="px-4 py-6 text-center text-sm text-fg-muted">
                No data fields
              </p>
            )}
          </div>
        </div>

        {/* Metadata */}
        <div className="rounded-lg bg-surface/30 p-4">
          <h4 className="mb-3 text-xs font-semibold uppercase tracking-wider text-fg-muted">
            Details
          </h4>
          <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
            <div>
              <span className="text-fg-muted">Type Version:</span>{' '}
              <span className="text-fg">{credential.typeVersion}</span>
            </div>
            <div>
              <span className="text-fg-muted">Created By:</span>{' '}
              <span className="text-fg">{credential.createdBy}</span>
            </div>
            <div>
              <span className="text-fg-muted">Created:</span>{' '}
              <span className="text-fg">{formatDateTime(credential.createdAt)}</span>
            </div>
            <div>
              <span className="text-fg-muted">Updated:</span>{' '}
              <span className="text-fg">{formatDateTime(credential.updatedAt)}</span>
            </div>
            <div>
              <span className="text-fg-muted">Updated By:</span>{' '}
              <span className="text-fg">{credential.updatedBy}</span>
            </div>
            <div>
              <span className="text-fg-muted">Last Accessed:</span>{' '}
              <span className="text-fg">{formatDateTime(credential.lastAccessedAt)}</span>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
}
