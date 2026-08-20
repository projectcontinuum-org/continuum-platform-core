import { useState, useEffect, useRef } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import type { KnimeWorkflowResponse } from '../types/api';

interface ReplaceWorkflowModalProps {
  workflow: KnimeWorkflowResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onReplace: (workflowId: string, file: File) => Promise<void>;
}

export function ReplaceWorkflowModal({ workflow, isOpen, onClose, onReplace }: ReplaceWorkflowModalProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setSelectedFile(null);
      setError(null);
    }
  }, [isOpen]);

  const handleFileChange = (file: File | null) => {
    if (file && !file.name.toLowerCase().endsWith('.knwf')) {
      setError('Only .knwf files are supported');
      setSelectedFile(null);
      return;
    }
    setError(null);
    setSelectedFile(file);
  };

  const handleReplace = async () => {
    if (!workflow || !selectedFile) return;

    setLoading(true);
    setError(null);

    try {
      await onReplace(workflow.workflowId, selectedFile);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to replace workflow');
    } finally {
      setLoading(false);
    }
  };

  if (!workflow) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Replace Workflow File" size="sm">
      <div className="space-y-4">
        {error && (
          <div className="rounded-lg bg-red-100 p-3 text-sm text-red-800 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}

        <p className="text-sm text-fg-muted">
          Replacing <span className="font-semibold text-fg">{workflow.fileName}</span>. Any schedules
          referencing this workflow will use the new file on their next run.
        </p>

        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="flex w-full flex-col items-center gap-2 rounded-xl border-2 border-dashed border-divider bg-surface/30 px-6 py-10 text-center transition-colors hover:border-accent/40"
        >
          <svg className="h-8 w-8 text-fg-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          {selectedFile ? (
            <span className="text-sm font-medium text-fg">{selectedFile.name}</span>
          ) : (
            <>
              <span className="text-sm font-medium text-fg">Click to choose a replacement .knwf file</span>
              <span className="text-xs text-fg-muted">KNIME workflow archive</span>
            </>
          )}
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept=".knwf"
          className="hidden"
          onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
        />

        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button onClick={handleReplace} loading={loading} disabled={!selectedFile}>
            Replace
          </Button>
        </div>
      </div>
    </Modal>
  );
}
