import { useState, useMemo } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { CustomSelect } from './CustomSelect';
import { DynamicFieldRenderer } from './DynamicFieldRenderer';
import { useTheme } from '../hooks/useTheme';
import type { CredentialResponse, CredentialUpdateRequest, CredentialTypeResponse } from '../types/api';

interface EditCredentialModalProps {
  credential: CredentialResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onUpdate: (name: string, request: CredentialUpdateRequest) => Promise<void>;
  credentialTypes: CredentialTypeResponse[];
  getTypeWithVersion: (typeName: string, version?: string) => CredentialTypeResponse | undefined;
}

// Stable fallback objects to avoid re-renders
const EMPTY_SCHEMA: Record<string, unknown> = {};
const EMPTY_UI_SCHEMA: Record<string, unknown> = {};

export function EditCredentialModal({
  credential,
  isOpen,
  onClose,
  onUpdate,
  credentialTypes,
  getTypeWithVersion,
}: EditCredentialModalProps) {
  const { isDark } = useTheme();
  const [data, setData] = useState<Record<string, string>>({});
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedVersion, setSelectedVersion] = useState('');

  // Track which credential we've synced from, so we sync synchronously
  // during render (not in useEffect which runs after render)
  const [syncedFrom, setSyncedFrom] = useState<string | null>(null);
  const credentialKey = credential?.name ?? null;

  if (isOpen && credentialKey !== null && credentialKey !== syncedFrom) {
    // Synchronous state update during render — runs before children mount
    setData(credential!.data ?? {});
    setDescription(credential!.description ?? '');
    setSelectedVersion(credential!.typeVersion);
    setError(null);
    setSyncedFrom(credentialKey);
  }

  // Reset tracking when modal closes
  if (!isOpen && syncedFrom !== null) {
    setSyncedFrom(null);
  }

  // Available versions for the credential's type
  const versionsForType = useMemo(() =>
    credentialTypes
      .filter(t => t.type === credential?.type)
      .map(t => t.version)
      .sort((a, b) => b.localeCompare(a, undefined, { numeric: true })),
    [credentialTypes, credential?.type],
  );

  // Get the type's schema — memoized for stable references
  const selectedTypeObj = useMemo(
    () => getTypeWithVersion(credential?.type ?? '', selectedVersion || credential?.typeVersion),
    [getTypeWithVersion, credential?.type, credential?.typeVersion, selectedVersion],
  );
  const schema = selectedTypeObj?.schema ?? EMPTY_SCHEMA;
  const uiSchema = selectedTypeObj?.uiSchema ?? EMPTY_UI_SCHEMA;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!credential) return;

    setLoading(true);
    setError(null);

    try {
      const request: CredentialUpdateRequest = {
        data,
        description: description.trim() || undefined,
      };

      // Include version if changed
      if (selectedVersion && selectedVersion !== credential.typeVersion) {
        request.typeVersion = selectedVersion;
      }

      await onUpdate(credential.name, request);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update credential');
    } finally {
      setLoading(false);
    }
  };

  if (!credential) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Edit Credential" size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && (
          <div className="rounded-lg bg-red-100 p-3 text-sm text-red-800 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}

        {/* Credential Name (read-only) */}
        <div>
          <label className="block text-sm font-medium text-fg">
            Credential Name
          </label>
          <input
            type="text"
            value={credential.name}
            disabled
            className="mt-1 w-full rounded-lg border border-divider bg-surface px-3 py-2 text-fg-muted"
          />
        </div>

        {/* Credential Type (read-only) */}
        <div>
          <label className="block text-sm font-medium text-fg">
            Credential Type
          </label>
          <input
            type="text"
            value={credential.type}
            disabled
            className="mt-1 w-full rounded-lg border border-divider bg-surface px-3 py-2 text-fg-muted"
          />
        </div>

        {/* Type Version (if multiple versions exist) */}
        {versionsForType.length > 1 && (
          <div>
            <label htmlFor="editCredVersion" className="block text-sm font-medium text-fg">
              Version
            </label>
            <div className="mt-1">
              <CustomSelect
                id="editCredVersion"
                value={selectedVersion}
                onChange={setSelectedVersion}
                options={versionsForType.map((v) => ({ value: v, label: v }))}
                placeholder="Select version..."
              />
            </div>
          </div>
        )}

        {/* Dynamic Fields (JSON Forms) */}
        <div>
          <label className="mb-2 block text-sm font-medium text-fg">
            Credential Data
          </label>
          <div className="rounded-lg border border-divider bg-surface/30 p-4">
            <DynamicFieldRenderer
              key={credential.name}
              type={credential.type}
              schema={schema}
              uiSchema={uiSchema}
              data={data}
              onChange={setData}
              isDark={isDark}
            />
          </div>
        </div>

        {/* Description */}
        <div>
          <label htmlFor="editCredDesc" className="block text-sm font-medium text-fg">
            Description <span className="text-xs font-normal text-fg-muted">(optional)</span>
          </label>
          <textarea
            id="editCredDesc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="A brief description of this credential..."
            rows={2}
            maxLength={1000}
            className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-2 text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
          />
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button type="submit" loading={loading}>
            Update Credential
          </Button>
        </div>
      </form>
    </Modal>
  );
}
