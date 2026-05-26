import { useState, useEffect } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import type { CredentialResponse } from '../types/api';

interface DeleteConfirmModalProps {
  credential: CredentialResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onDelete: (name: string) => Promise<void>;
}

export function DeleteConfirmModal({ credential, isOpen, onClose, onDelete }: DeleteConfirmModalProps) {
  const [confirmText, setConfirmText] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setConfirmText('');
      setError(null);
    }
  }, [isOpen]);

  const handleDelete = async () => {
    if (!credential || confirmText !== credential.name) return;

    setLoading(true);
    setError(null);

    try {
      await onDelete(credential.name);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete credential');
    } finally {
      setLoading(false);
    }
  };

  if (!credential) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Delete Credential" size="sm">
      <div className="space-y-4">
        {error && (
          <div className="rounded-lg bg-red-100 p-3 text-sm text-red-800 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}

        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
            <svg className="h-5 w-5 text-red-600 dark:text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <div>
            <p className="text-sm text-fg">
              This action cannot be undone. This will permanently delete the credential{' '}
              <span className="font-semibold text-fg">"{credential.name}"</span> and remove
              all associated data.
            </p>
          </div>
        </div>

        <div>
          <label htmlFor="deleteConfirm" className="block text-sm text-fg-muted">
            Type <span className="font-mono font-semibold text-fg">{credential.name}</span> to confirm
          </label>
          <input
            id="deleteConfirm"
            type="text"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder={credential.name}
            className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-2 text-fg placeholder:text-fg-muted/50 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500"
            autoComplete="off"
          />
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button
            variant="danger"
            onClick={handleDelete}
            loading={loading}
            disabled={confirmText !== credential.name}
          >
            Delete Credential
          </Button>
        </div>
      </div>
    </Modal>
  );
}
