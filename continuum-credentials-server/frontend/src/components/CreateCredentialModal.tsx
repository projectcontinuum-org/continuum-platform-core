import { useState, useEffect, useMemo } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { CustomSelect } from './CustomSelect';
import { DynamicFieldRenderer } from './DynamicFieldRenderer';
import { useTheme } from '../hooks/useTheme';
import type { CredentialCreateRequest, CredentialTypeResponse } from '../types/api';

interface CreateCredentialModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (request: CredentialCreateRequest) => Promise<void>;
  credentialTypes: CredentialTypeResponse[];
  uniqueTypeNames: string[];
  getTypeWithVersion: (typeName: string, version?: string) => CredentialTypeResponse | undefined;
}

// Stable fallback objects to avoid re-renders
const EMPTY_SCHEMA: Record<string, unknown> = {};
const EMPTY_UI_SCHEMA: Record<string, unknown> = {};

export function CreateCredentialModal({
  isOpen,
  onClose,
  onCreate,
  credentialTypes,
  uniqueTypeNames,
  getTypeWithVersion,
}: CreateCredentialModalProps) {
  const { isDark } = useTheme();
  const [name, setName] = useState('');
  const [selectedType, setSelectedType] = useState('');
  const [selectedVersion, setSelectedVersion] = useState('');
  const [data, setData] = useState<Record<string, string>>({});
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Available versions for the selected type
  const versionsForType = useMemo(() =>
    credentialTypes
      .filter(t => t.type === selectedType)
      .map(t => t.version)
      .sort((a, b) => b.localeCompare(a, undefined, { numeric: true })),
    [credentialTypes, selectedType],
  );

  // Get the selected type's schema — memoized to keep stable references
  const selectedTypeObj = useMemo(
    () => getTypeWithVersion(selectedType, selectedVersion),
    [getTypeWithVersion, selectedType, selectedVersion],
  );
  const schema = selectedTypeObj?.schema ?? EMPTY_SCHEMA;
  const uiSchema = selectedTypeObj?.uiSchema ?? EMPTY_UI_SCHEMA;

  // Reset form when type changes
  useEffect(() => {
    setData({});
    if (versionsForType.length > 0 && !versionsForType.includes(selectedVersion)) {
      setSelectedVersion(versionsForType[0]);
    }
  }, [selectedType]); // eslint-disable-line react-hooks/exhaustive-deps

  // Reset form when modal opens
  useEffect(() => {
    if (isOpen) {
      setName('');
      setSelectedType(uniqueTypeNames[0] ?? '');
      setSelectedVersion('');
      setData({});
      setDescription('');
      setError(null);
    }
  }, [isOpen, uniqueTypeNames]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!name.trim()) {
      setError('Credential name is required');
      return;
    }

    if (!/^[a-zA-Z0-9_-]+$/.test(name)) {
      setError('Name must contain only letters, numbers, hyphens, and underscores');
      return;
    }

    if (!selectedType) {
      setError('Please select a credential type');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await onCreate({
        name: name.trim(),
        type: selectedType,
        typeVersion: selectedVersion || versionsForType[0] || '1.0.0',
        data,
        description: description.trim() || undefined,
      });

      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create credential');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Create New Credential" size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && (
          <div className="rounded-lg bg-red-100 p-3 text-sm text-red-800 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}

        {/* Credential Name */}
        <div>
          <label htmlFor="credName" className="block text-sm font-medium text-fg">
            Credential Name <span className="text-red-500">*</span>
          </label>
          <input
            id="credName"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="my-aws-credentials"
            className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-2 text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            required
          />
          <p className="mt-1 text-xs text-fg-muted">
            Letters, numbers, hyphens, and underscores only
          </p>
        </div>

        {/* Credential Type */}
        <div>
          <label htmlFor="credType" className="block text-sm font-medium text-fg">
            Credential Type <span className="text-red-500">*</span>
          </label>
          <div className="mt-1">
            <CustomSelect
              id="credType"
              value={selectedType}
              onChange={setSelectedType}
              options={uniqueTypeNames.map((t) => ({ value: t, label: t }))}
              placeholder="Select a type..."
              required
            />
          </div>
        </div>

        {/* Type Version (if multiple versions exist) */}
        {versionsForType.length > 1 && (
          <div>
            <label htmlFor="credVersion" className="block text-sm font-medium text-fg">
              Version
            </label>
            <div className="mt-1">
              <CustomSelect
                id="credVersion"
                value={selectedVersion}
                onChange={setSelectedVersion}
                options={versionsForType.map((v) => ({ value: v, label: v }))}
                placeholder="Select version..."
              />
            </div>
          </div>
        )}

        {/* Dynamic Fields (JSON Forms) */}
        {selectedType && (
          <div>
            <label className="mb-2 block text-sm font-medium text-fg">
              Credential Data
            </label>
            <div className="rounded-lg border border-divider bg-surface/30 p-4">
              <DynamicFieldRenderer
                type={selectedType}
                schema={schema}
                uiSchema={uiSchema}
                data={data}
                onChange={setData}
                isDark={isDark}
              />
            </div>
          </div>
        )}

        {/* Description */}
        <div>
          <label htmlFor="credDesc" className="block text-sm font-medium text-fg">
            Description <span className="text-xs font-normal text-fg-muted">(optional)</span>
          </label>
          <textarea
            id="credDesc"
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
            Create Credential
          </Button>
        </div>
      </form>
    </Modal>
  );
}
