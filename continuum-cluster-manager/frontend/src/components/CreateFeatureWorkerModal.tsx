import { useState, useEffect } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { CustomSelect } from './CustomSelect';
import { TASK_QUEUE_PATTERN, DEFAULT_FEATURE_WORKER_RESOURCES } from '../types/api';
import type { FeatureWorkerCreateRequest, FeatureWorkerResourceSpec, AutoscalingSpec } from '../types/api';
import { featureWorkerApi } from '../api/feature-workers';

interface CreateFeatureWorkerModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (request: FeatureWorkerCreateRequest) => Promise<void>;
}

interface EnvVarRow {
  key: string;
  value: string;
}

export function CreateFeatureWorkerModal({ isOpen, onClose, onCreate }: CreateFeatureWorkerModalProps) {
  const [workerName, setWorkerName] = useState('');
  const [image, setImage] = useState('');
  const [taskQueue, setTaskQueue] = useState('');
  const [replicas, setReplicas] = useState(1);
  const [resources, setResources] = useState<FeatureWorkerResourceSpec>(DEFAULT_FEATURE_WORKER_RESOURCES);
  const [envVars, setEnvVars] = useState<EnvVarRow[]>([]);
  const [pvcEnabled, setPvcEnabled] = useState(false);
  const [storageSize, setStorageSize] = useState('5Gi');
  const [autoscalingEnabled, setAutoscalingEnabled] = useState(false);
  const [minReplicas, setMinReplicas] = useState(1);
  const [maxReplicas, setMaxReplicas] = useState(3);
  const [targetCpuPercent, setTargetCpuPercent] = useState(80);
  const [variant, setVariant] = useState('');
  const [availableVariants, setAvailableVariants] = useState<string[]>([]);
  const [variantsLoading, setVariantsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setVariantsLoading(true);
      featureWorkerApi.getAvailableVariants()
        .then((v) => setAvailableVariants(v))
        .catch(() => setAvailableVariants([]))
        .finally(() => setVariantsLoading(false));
    }
  }, [isOpen]);

  const taskQueueValid = taskQueue.length === 0 || TASK_QUEUE_PATTERN.test(taskQueue);

  const resetForm = () => {
    setWorkerName('');
    setImage('');
    setTaskQueue('');
    setReplicas(1);
    setResources(DEFAULT_FEATURE_WORKER_RESOURCES);
    setEnvVars([]);
    setPvcEnabled(false);
    setStorageSize('5Gi');
    setAutoscalingEnabled(false);
    setMinReplicas(1);
    setMaxReplicas(3);
    setTargetCpuPercent(80);
    setVariant('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!workerName.trim()) {
      setError('Worker name is required');
      return;
    }
    if (!/^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$/.test(workerName)) {
      setError('Worker name must be lowercase, start and end with alphanumeric characters, and can contain hyphens');
      return;
    }
    if (!image.trim()) {
      setError('Container image is required');
      return;
    }
    if (!taskQueue.trim()) {
      setError('Task queue is required');
      return;
    }
    if (!TASK_QUEUE_PATTERN.test(taskQueue)) {
      setError('Task queue must match CONTINUUM-FEATURE-{feature-name}-TASK-QUEUE');
      return;
    }

    const envVarsMap = Object.fromEntries(
      envVars.filter((row) => row.key.trim()).map((row) => [row.key.trim(), row.value])
    );

    const autoscaling: AutoscalingSpec | undefined = autoscalingEnabled
      ? {
          enabled: true,
          minReplicas,
          maxReplicas,
          targetCPUUtilizationPercentage: targetCpuPercent,
        }
      : undefined;

    setLoading(true);
    setError(null);

    try {
      await onCreate({
        workerName: workerName.trim(),
        image: image.trim(),
        taskQueue: taskQueue.trim(),
        resources: {
          ...resources,
          storageSize: pvcEnabled ? storageSize : null,
        },
        replicas,
        autoscaling,
        pvcEnabled,
        envVars: envVarsMap,
        ...(variant && { variant }),
      });

      resetForm();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create feature worker');
    } finally {
      setLoading(false);
    }
  };

  const updateEnvVar = (index: number, field: 'key' | 'value', value: string) => {
    setEnvVars((prev) => prev.map((row, i) => (i === index ? { ...row, [field]: value } : row)));
  };

  const addEnvVar = () => setEnvVars((prev) => [...prev, { key: '', value: '' }]);
  const removeEnvVar = (index: number) => setEnvVars((prev) => prev.filter((_, i) => i !== index));

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Create New Feature Worker" size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && (
          <div className="rounded-lg bg-red-100 p-3 text-sm text-red-800 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}

        {/* Worker Name */}
        <div>
          <label htmlFor="workerName" className="block text-sm font-medium text-fg">
            Worker Name <span className="text-red-500">*</span>
          </label>
          <input
            id="workerName"
            type="text"
            value={workerName}
            onChange={(e) => setWorkerName(e.target.value.toLowerCase())}
            placeholder="my-feature-worker"
            className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-2 text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            required
          />
          <p className="mt-1 text-xs text-fg-muted">
            Lowercase letters, numbers, and hyphens only
          </p>
        </div>

        {/* Image */}
        <div>
          <label htmlFor="image" className="block text-sm font-medium text-fg">
            Container Image <span className="text-red-500">*</span>
          </label>
          <input
            id="image"
            type="text"
            value={image}
            onChange={(e) => setImage(e.target.value)}
            placeholder="projectcontinuum/continuum-feature-myworker:latest"
            className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-2 text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            required
          />
        </div>

        {/* Task Queue */}
        <div>
          <label htmlFor="taskQueue" className="block text-sm font-medium text-fg">
            Task Queue <span className="text-red-500">*</span>
          </label>
          <input
            id="taskQueue"
            type="text"
            value={taskQueue}
            onChange={(e) => setTaskQueue(e.target.value.toUpperCase())}
            placeholder="CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE"
            className={`mt-1 w-full rounded-lg border bg-base px-3 py-2 font-mono text-sm text-fg placeholder:text-fg-muted/50 focus:outline-none focus:ring-1 ${
              taskQueueValid
                ? 'border-divider focus:border-accent focus:ring-accent'
                : 'border-red-500 focus:border-red-500 focus:ring-red-500'
            }`}
            required
          />
          <p className={`mt-1 text-xs ${taskQueueValid ? 'text-fg-muted' : 'text-red-500'}`}>
            Must match CONTINUUM-FEATURE-{'{feature-name}'}-TASK-QUEUE and be unique across all feature workers
          </p>
        </div>

        {/* Overlay Variant */}
        {!variantsLoading && availableVariants.length > 0 && (
          <div>
            <label htmlFor="variant" className="block text-sm font-medium text-fg">
              Overlay Variant
            </label>
            <div className="mt-1">
              <CustomSelect
                id="variant"
                value={variant}
                onChange={setVariant}
                options={[
                  { value: '', label: 'Default (no overlay)' },
                  ...availableVariants.map((v) => ({
                    value: v,
                    label: v.split('-').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' '),
                  })),
                ]}
                placeholder="Select variant..."
              />
            </div>
          </div>
        )}

        {/* Replicas / Autoscaling */}
        <div className="rounded-lg border border-divider bg-surface/50 p-4 space-y-3">
          <div className="flex items-center justify-between">
            <label htmlFor="autoscalingEnabled" className="text-sm font-medium text-fg">
              Autoscaling
            </label>
            <button
              id="autoscalingEnabled"
              type="button"
              onClick={() => setAutoscalingEnabled(!autoscalingEnabled)}
              className={`relative h-6 w-11 rounded-full transition-colors ${autoscalingEnabled ? 'bg-accent' : 'bg-divider'}`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform ${autoscalingEnabled ? 'translate-x-5' : 'translate-x-0.5'}`}
              />
            </button>
          </div>

          {!autoscalingEnabled ? (
            <div>
              <label htmlFor="replicas" className="block text-sm text-fg-muted">
                Replicas
              </label>
              <input
                id="replicas"
                type="number"
                min={0}
                value={replicas}
                onChange={(e) => setReplicas(parseInt(e.target.value) || 0)}
                className="mt-1 w-24 rounded-lg border border-divider bg-base px-3 py-1.5 text-sm text-fg focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              />
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label htmlFor="minReplicas" className="block text-xs text-fg-muted">Min Replicas</label>
                <input
                  id="minReplicas"
                  type="number"
                  min={1}
                  value={minReplicas}
                  onChange={(e) => setMinReplicas(parseInt(e.target.value) || 1)}
                  className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-1.5 text-sm text-fg focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
              <div>
                <label htmlFor="maxReplicas" className="block text-xs text-fg-muted">Max Replicas</label>
                <input
                  id="maxReplicas"
                  type="number"
                  min={minReplicas}
                  value={maxReplicas}
                  onChange={(e) => setMaxReplicas(parseInt(e.target.value) || minReplicas)}
                  className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-1.5 text-sm text-fg focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
              <div>
                <label htmlFor="targetCpuPercent" className="block text-xs text-fg-muted">Target CPU %</label>
                <input
                  id="targetCpuPercent"
                  type="number"
                  min={1}
                  max={100}
                  value={targetCpuPercent}
                  onChange={(e) => setTargetCpuPercent(parseInt(e.target.value) || 80)}
                  className="mt-1 w-full rounded-lg border border-divider bg-base px-3 py-1.5 text-sm text-fg focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
            </div>
          )}
        </div>

        {/* PVC Toggle */}
        <div className="rounded-lg border border-divider bg-surface/50 p-4 space-y-3">
          <div className="flex items-center justify-between">
            <label htmlFor="pvcEnabled" className="text-sm font-medium text-fg">
              Persistent Storage
            </label>
            <button
              id="pvcEnabled"
              type="button"
              onClick={() => setPvcEnabled(!pvcEnabled)}
              className={`relative h-6 w-11 rounded-full transition-colors ${pvcEnabled ? 'bg-accent' : 'bg-divider'}`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform ${pvcEnabled ? 'translate-x-5' : 'translate-x-0.5'}`}
              />
            </button>
          </div>
          {pvcEnabled && (
            <div>
              <label htmlFor="storageSize" className="block text-xs text-fg-muted">Storage Size</label>
              <input
                id="storageSize"
                type="text"
                value={storageSize}
                onChange={(e) => setStorageSize(e.target.value)}
                placeholder="5Gi"
                className="mt-1 w-32 rounded-lg border border-divider bg-base px-3 py-1.5 text-sm text-fg focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              />
            </div>
          )}
        </div>

        {/* Env Vars */}
        <div>
          <div className="mb-2 flex items-center justify-between">
            <label className="text-sm font-medium text-fg">Environment Variables</label>
            <button
              type="button"
              onClick={addEnvVar}
              className="flex items-center gap-1 text-xs text-accent hover:text-highlight"
            >
              <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              Add Variable
            </button>
          </div>
          <div className="space-y-2">
            {envVars.map((row, i) => (
              <div key={i} className="flex items-center gap-2">
                <input
                  type="text"
                  value={row.key}
                  onChange={(e) => updateEnvVar(i, 'key', e.target.value)}
                  placeholder="KEY"
                  className="w-1/2 rounded-lg border border-divider bg-base px-3 py-1.5 text-sm font-mono text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
                <input
                  type="text"
                  value={row.value}
                  onChange={(e) => updateEnvVar(i, 'value', e.target.value)}
                  placeholder="value"
                  className="w-1/2 rounded-lg border border-divider bg-base px-3 py-1.5 text-sm font-mono text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
                <button
                  type="button"
                  onClick={() => removeEnvVar(i)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-surface hover:text-red-500"
                >
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            ))}
            {envVars.length === 0 && (
              <p className="text-xs text-fg-muted">No environment variables configured</p>
            )}
          </div>
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button type="submit" loading={loading}>
            Create Feature Worker
          </Button>
        </div>
      </form>
    </Modal>
  );
}
