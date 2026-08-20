import { useState, useEffect, useRef } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';

interface UploadWorkflowModalProps {
  isOpen: boolean;
  onClose: () => void;
  onUpload: (file: File) => Promise<void>;
}

export function UploadWorkflowModal({ isOpen, onClose, onUpload }: UploadWorkflowModalProps) {
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

  const handleUpload = async () => {
    if (!selectedFile) return;

    setLoading(true);
    setError(null);

    try {
      await onUpload(selectedFile);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to upload workflow');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Upload Workflow" size="sm">
      <div className="space-y-4">
        {error && (
          <div className="rounded-lg bg-red-100 p-3 text-sm text-red-800 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}

        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="flex w-full flex-col items-center gap-2 rounded-xl border-2 border-dashed border-divider bg-surface/30 px-6 py-10 text-center transition-colors hover:border-accent/40"
        >
          <svg className="h-8 w-8 text-fg-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
          </svg>
          {selectedFile ? (
            <span className="text-sm font-medium text-fg">{selectedFile.name}</span>
          ) : (
            <>
              <span className="text-sm font-medium text-fg">Click to choose a .knwf file</span>
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
          <Button onClick={handleUpload} loading={loading} disabled={!selectedFile}>
            Upload
          </Button>
        </div>
      </div>
    </Modal>
  );
}
