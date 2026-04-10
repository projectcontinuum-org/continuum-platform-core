import { useState, useEffect, useRef } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { DEFAULT_IMAGE_TAG, WORKBENCH_IMAGE_REPOSITORY } from '../types/api';
import type { WorkbenchCreateRequest, ResourceSpec, DockerHubTag } from '../types/api';
import { workbenchApi } from '../api/workbench';

interface CreateWorkbenchModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (request: WorkbenchCreateRequest) => Promise<void>;
}

// ── Preset sizes ────────────────────────────────────────────────────
interface SizePreset {
  label: string;
  description: string;
  icon: string;
  resources: ResourceSpec;
}

const SIZE_PRESETS: SizePreset[] = [
  {
    label: 'Small',
    description: 'Light tasks & exploration',
    icon: '🟢',
    resources: {
      cpuRequest: '500m', cpuLimit: '1',
      memoryRequest: '512Mi', memoryLimit: '1Gi',
      storageSize: '5Gi', storageClassName: null,
    },
  },
  {
    label: 'Medium',
    description: 'General workflow development',
    icon: '🔵',
    resources: {
      cpuRequest: '1', cpuLimit: '2',
      memoryRequest: '1Gi', memoryLimit: '2Gi',
      storageSize: '10Gi', storageClassName: null,
    },
  },
  {
    label: 'Large',
    description: 'Data-intensive workloads',
    icon: '🟣',
    resources: {
      cpuRequest: '2', cpuLimit: '4',
      memoryRequest: '2Gi', memoryLimit: '4Gi',
      storageSize: '20Gi', storageClassName: null,
    },
  },
  {
    label: 'XL',
    description: 'Heavy compute & large datasets',
    icon: '🟠',
    resources: {
      cpuRequest: '4', cpuLimit: '8',
      memoryRequest: '4Gi', memoryLimit: '8Gi',
      storageSize: '50Gi', storageClassName: null,
    },
  },
];

// ── Slider step options ─────────────────────────────────────────────
const CPU_OPTIONS = ['250m', '500m', '1', '2', '4', '8'];
const MEMORY_OPTIONS = ['256Mi', '512Mi', '1Gi', '2Gi', '4Gi', '8Gi', '16Gi'];
const STORAGE_OPTIONS = ['1Gi', '2Gi', '5Gi', '10Gi', '20Gi', '50Gi', '100Gi'];

function formatCpu(val: string): string {
  if (val.endsWith('m')) return `${parseInt(val)}m`;
  return `${val} core${val === '1' ? '' : 's'}`;
}

function formatLabel(val: string): string {
  return val;
}

// ── Stepped Slider Component ────────────────────────────────────────
interface SteppedSliderProps {
  id: string;
  label: string;
  options: string[];
  value: string;
  onChange: (val: string) => void;
  formatValue?: (val: string) => string;
  icon: React.ReactNode;
  color: string;
}

function SteppedSlider({ id, label, options, value, onChange, formatValue = formatLabel, icon, color }: SteppedSliderProps) {
  const idx = options.indexOf(value);
  const currentIdx = idx >= 0 ? idx : 0;
  const pct = options.length > 1 ? (currentIdx / (options.length - 1)) * 100 : 0;

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <label htmlFor={id} className="flex items-center gap-2 text-sm font-medium text-fg">
          {icon}
          {label}
        </label>
        <span className={`rounded-md px-2 py-0.5 text-xs font-semibold ${color}`}>
          {formatValue(value)}
        </span>
      </div>
      <div className="relative">
        <input
          id={id}
          type="range"
          min={0}
          max={options.length - 1}
          step={1}
          value={currentIdx}
          onChange={(e) => onChange(options[parseInt(e.target.value)])}
          className="slider-input w-full cursor-pointer"
          style={{
            background: `linear-gradient(to right, var(--slider-fill, #7c5cfc) ${pct}%, var(--slider-track, #e2e2e2) ${pct}%)`,
          }}
        />
        <div className="mt-1 flex justify-between px-0.5">
          {options.map((opt, i) => (
            <span
              key={opt}
              className={`text-[10px] ${i === currentIdx ? 'font-semibold text-accent' : 'text-fg-muted/60'}`}
            >
              {opt}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── Main Component ──────────────────────────────────────────────────
export function CreateWorkbenchModal({ isOpen, onClose, onCreate }: CreateWorkbenchModalProps) {
  const [instanceName, setInstanceName] = useState('');
  const [imageTag, setImageTag] = useState(DEFAULT_IMAGE_TAG);
  const [resources, setResources] = useState<ResourceSpec>(SIZE_PRESETS[0].resources);
  const [selectedPreset, setSelectedPreset] = useState(0);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Tag dropdown state
  const [availableTags, setAvailableTags] = useState<DockerHubTag[]>([]);
  const [tagsLoading, setTagsLoading] = useState(false);
  const [tagsError, setTagsError] = useState(false);
  const [showTagDropdown, setShowTagDropdown] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const filteredTags = availableTags.filter(
    (tag) => tag.name.toLowerCase().includes(imageTag.toLowerCase())
  );

  // Fetch tags when modal opens
  useEffect(() => {
    if (isOpen) {
      setTagsLoading(true);
      setTagsError(false);
      workbenchApi.getAvailableTags()
        .then((tags) => setAvailableTags(tags))
        .catch(() => setTagsError(true))
        .finally(() => setTagsLoading(false));
    }
  }, [isOpen]);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowTagDropdown(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!instanceName.trim()) {
      setError('Instance name is required');
      return;
    }

    if (!/^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$/.test(instanceName)) {
      setError('Instance name must be lowercase, start and end with alphanumeric characters, and can contain hyphens');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await onCreate({
        instanceName: instanceName.trim(),
        image: `${WORKBENCH_IMAGE_REPOSITORY}:${imageTag}`,
        resources,
      });

      setInstanceName('');
      setImageTag(DEFAULT_IMAGE_TAG);
      setResources(SIZE_PRESETS[0].resources);
      setSelectedPreset(0);
      setShowAdvanced(false);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create workbench');
    } finally {
      setLoading(false);
    }
  };

  const selectPreset = (index: number) => {
    setSelectedPreset(index);
    setResources({ ...SIZE_PRESETS[index].resources });
    setShowAdvanced(false);
  };

  const updateResource = (key: keyof ResourceSpec, value: string) => {
    setResources(prev => ({ ...prev, [key]: value }));
    setSelectedPreset(-1); // deselect preset when manually changed
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Create New Workbench" size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && (
          <div className="rounded-lg bg-red-100 p-3 text-sm text-red-800 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}

        {/* Instance Name */}
        <div>
          <label htmlFor="instanceName" className="block text-sm font-medium text-fg">
            Instance Name <span className="text-red-500">*</span>
          </label>
          <input
            id="instanceName"
            type="text"
            value={instanceName}
            onChange={(e) => setInstanceName(e.target.value.toLowerCase())}
            placeholder="my-workbench"
            className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-2 text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            required
          />
          <p className="mt-1 text-xs text-fg-muted">
            Lowercase letters, numbers, and hyphens only
          </p>
        </div>

        {/* Image */}
        <div>
          <label htmlFor="imageTag" className="block text-sm font-medium text-fg">
            Container Image
          </label>
          <div className="mt-1 relative" ref={dropdownRef}>
            <div className="flex rounded-lg border border-divider bg-base overflow-hidden focus-within:border-accent focus-within:ring-1 focus-within:ring-accent">
              <span className="flex items-center bg-surface px-3 text-sm text-fg-muted border-r border-divider select-none whitespace-nowrap">
                {WORKBENCH_IMAGE_REPOSITORY}:
              </span>
              <input
                id="imageTag"
                type="text"
                value={imageTag}
                onChange={(e) => {
                  setImageTag(e.target.value);
                  setShowTagDropdown(true);
                }}
                onFocus={() => setShowTagDropdown(true)}
                placeholder={tagsLoading ? 'Loading...' : DEFAULT_IMAGE_TAG}
                className="w-[80%] bg-base px-3 py-2 text-fg placeholder:text-fg-muted/50 focus:outline-none"
                autoComplete="off"
              />
              {tagsLoading && (
                <span className="flex items-center px-2">
                  <svg className="animate-spin h-4 w-4 text-fg-muted" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                </span>
              )}
            </div>
            {showTagDropdown && filteredTags.length > 0 && (
              <div className="absolute z-10 mt-1 w-full max-h-48 overflow-y-auto rounded-lg border border-divider bg-base shadow-lg">
                {filteredTags.map((tag) => (
                  <button
                    key={tag.name}
                    type="button"
                    onClick={() => {
                      setImageTag(tag.name);
                      setShowTagDropdown(false);
                    }}
                    className={`w-full text-left px-3 py-2 text-sm hover:bg-surface transition-colors flex items-center justify-between ${
                      tag.name === imageTag ? 'bg-accent/10 text-accent' : 'text-fg'
                    }`}
                  >
                    <span className="font-medium">{tag.name}</span>
                    {tag.lastUpdated && (
                      <span className="text-xs text-fg-muted ml-2">
                        {new Date(tag.lastUpdated).toLocaleDateString()}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            )}
          </div>
          {tagsError && (
            <p className="mt-1 text-xs text-fg-muted">
              Could not load available tags. You can type a tag manually.
            </p>
          )}
        </div>

        {/* Size Presets */}
        <div>
          <label className="block text-sm font-medium text-fg mb-2">Workbench Size</label>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {SIZE_PRESETS.map((preset, i) => (
              <button
                key={preset.label}
                type="button"
                onClick={() => selectPreset(i)}
                className={`group relative flex flex-col items-center rounded-lg border-2 p-3 text-center transition-all
                  ${selectedPreset === i
                    ? 'border-accent bg-accent/10 shadow-sm'
                    : 'border-divider bg-surface/50 hover:border-accent/40 hover:bg-surface'
                  }`}
              >
                <span className="text-xl">{preset.icon}</span>
                <span className={`mt-1 text-sm font-semibold ${selectedPreset === i ? 'text-accent' : 'text-fg'}`}>
                  {preset.label}
                </span>
                <span className="mt-0.5 text-[10px] leading-tight text-fg-muted">
                  {preset.description}
                </span>
                <div className="mt-2 flex flex-wrap justify-center gap-1">
                  <span className="rounded bg-surface px-1.5 py-0.5 text-[10px] text-fg-muted">
                    {preset.resources.cpuLimit} CPU
                  </span>
                  <span className="rounded bg-surface px-1.5 py-0.5 text-[10px] text-fg-muted">
                    {preset.resources.memoryLimit} RAM
                  </span>
                  <span className="rounded bg-surface px-1.5 py-0.5 text-[10px] text-fg-muted">
                    {preset.resources.storageSize}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Advanced Toggle */}
        <button
          type="button"
          onClick={() => setShowAdvanced(!showAdvanced)}
          className="flex items-center gap-2 text-sm text-accent hover:text-highlight"
        >
          <svg
            className={`h-4 w-4 transition-transform ${showAdvanced ? 'rotate-90' : ''}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
          Fine-tune Resources
        </button>

        {/* Advanced Sliders */}
        {showAdvanced && (
          <div className="space-y-5 rounded-lg border border-divider bg-surface/50 p-4">
            {/* CPU */}
            <div className="space-y-4">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-fg-muted">CPU</h4>
              <SteppedSlider
                id="cpuRequest"
                label="Request (guaranteed)"
                options={CPU_OPTIONS}
                value={resources.cpuRequest}
                onChange={(v) => updateResource('cpuRequest', v)}
                formatValue={formatCpu}
                icon={
                  <svg className="h-4 w-4 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z" />
                  </svg>
                }
                color="bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300"
              />
              <SteppedSlider
                id="cpuLimit"
                label="Limit (burstable max)"
                options={CPU_OPTIONS}
                value={resources.cpuLimit}
                onChange={(v) => updateResource('cpuLimit', v)}
                formatValue={formatCpu}
                icon={
                  <svg className="h-4 w-4 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                }
                color="bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300"
              />
            </div>

            <hr className="border-divider" />

            {/* Memory */}
            <div className="space-y-4">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-fg-muted">Memory</h4>
              <SteppedSlider
                id="memoryRequest"
                label="Request (guaranteed)"
                options={MEMORY_OPTIONS}
                value={resources.memoryRequest}
                onChange={(v) => updateResource('memoryRequest', v)}
                icon={
                  <svg className="h-4 w-4 text-purple-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                  </svg>
                }
                color="bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300"
              />
              <SteppedSlider
                id="memoryLimit"
                label="Limit (burstable max)"
                options={MEMORY_OPTIONS}
                value={resources.memoryLimit}
                onChange={(v) => updateResource('memoryLimit', v)}
                icon={
                  <svg className="h-4 w-4 text-purple-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                }
                color="bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300"
              />
            </div>

            <hr className="border-divider" />

            {/* Storage */}
            <div className="space-y-4">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-fg-muted">Storage</h4>
              <SteppedSlider
                id="storageSize"
                label="Persistent Volume Size"
                options={STORAGE_OPTIONS}
                value={resources.storageSize}
                onChange={(v) => updateResource('storageSize', v)}
                icon={
                  <svg className="h-4 w-4 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
                  </svg>
                }
                color="bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300"
              />
              <div>
                <label htmlFor="storageClassName" className="flex items-center gap-2 text-sm font-medium text-fg">
                  <svg className="h-4 w-4 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2" />
                  </svg>
                  Storage Class
                  <span className="text-xs font-normal text-fg-muted">(optional)</span>
                </label>
                <input
                  id="storageClassName"
                  type="text"
                  value={resources.storageClassName || ''}
                  onChange={(e) => updateResource('storageClassName', e.target.value || null as unknown as string)}
                  placeholder="default"
                  className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-2 text-sm text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
            </div>

            {/* Resource Summary */}
            <div className="rounded-lg bg-base p-3">
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-wider text-fg-muted">Summary</h4>
              <div className="grid grid-cols-3 gap-3 text-center">
                <div>
                  <div className="text-lg font-bold text-blue-600 dark:text-blue-400">{resources.cpuLimit}</div>
                  <div className="text-[10px] text-fg-muted">CPU cores</div>
                </div>
                <div>
                  <div className="text-lg font-bold text-purple-600 dark:text-purple-400">{resources.memoryLimit}</div>
                  <div className="text-[10px] text-fg-muted">Memory</div>
                </div>
                <div>
                  <div className="text-lg font-bold text-amber-600 dark:text-amber-400">{resources.storageSize}</div>
                  <div className="text-[10px] text-fg-muted">Storage</div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button type="submit" loading={loading}>
            Create Workbench
          </Button>
        </div>
      </form>
    </Modal>
  );
}

