import { useState, useEffect } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import type { KnimeWorkflowResponse, CreateKnimeWorkflowScheduleRequest } from '../types/api';

interface CreateScheduleModalProps {
  isOpen: boolean;
  onClose: () => void;
  workflows: KnimeWorkflowResponse[];
  onCreate: (request: CreateKnimeWorkflowScheduleRequest) => Promise<void>;
}

const emptyForm: CreateKnimeWorkflowScheduleRequest = {
  name: '',
  cronExpression: '',
  timeZone: '',
  knimeWorkflowId: '',
  resetWorkflow: true,
  timeoutSeconds: 300,
};

export function CreateScheduleModal({ isOpen, onClose, workflows, onCreate }: CreateScheduleModalProps) {
  const [form, setForm] = useState<CreateKnimeWorkflowScheduleRequest>(emptyForm);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setForm({ ...emptyForm, knimeWorkflowId: workflows[0]?.workflowId ?? '' });
      setError(null);
    }
  }, [isOpen, workflows]);

  const handleSubmit = async () => {
    if (!form.name.trim() || !form.cronExpression.trim() || !form.knimeWorkflowId) {
      setError('Name, cron expression, and workflow are required');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await onCreate({
        ...form,
        timeZone: form.timeZone?.trim() ? form.timeZone.trim() : undefined,
      });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create schedule');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Create Schedule" size="md">
      <div className="space-y-4">
        {error && (
          <div className="rounded-lg bg-red-100 p-3 text-sm text-red-800 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}

        <div>
          <label className="mb-1 block text-sm font-medium text-fg">Name</label>
          <input
            type="text"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            className="w-full rounded-lg border border-divider bg-surface px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
            placeholder="Nightly ETL run"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-fg">Workflow</label>
          <select
            value={form.knimeWorkflowId}
            onChange={(e) => setForm((f) => ({ ...f, knimeWorkflowId: e.target.value }))}
            className="w-full rounded-lg border border-divider bg-surface px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
          >
            {workflows.length === 0 && <option value="">No workflows uploaded</option>}
            {workflows.map((wf) => (
              <option key={wf.workflowId} value={wf.workflowId}>
                {wf.fileName}
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-fg">Cron Expression</label>
            <input
              type="text"
              value={form.cronExpression}
              onChange={(e) => setForm((f) => ({ ...f, cronExpression: e.target.value }))}
              className="w-full rounded-lg border border-divider bg-surface px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
              placeholder="0 0 2 * * *"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-fg">Time Zone (optional)</label>
            <input
              type="text"
              value={form.timeZone}
              onChange={(e) => setForm((f) => ({ ...f, timeZone: e.target.value }))}
              className="w-full rounded-lg border border-divider bg-surface px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
              placeholder="UTC"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-fg">Timeout (seconds)</label>
            <input
              type="number"
              min={1}
              value={form.timeoutSeconds}
              onChange={(e) => setForm((f) => ({ ...f, timeoutSeconds: Number(e.target.value) }))}
              className="w-full rounded-lg border border-divider bg-surface px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
            />
          </div>
          <div className="flex items-end pb-2">
            <label className="flex items-center gap-2 text-sm text-fg">
              <input
                type="checkbox"
                checked={form.resetWorkflow}
                onChange={(e) => setForm((f) => ({ ...f, resetWorkflow: e.target.checked }))}
                className="h-4 w-4 rounded border-divider text-accent focus:ring-accent"
              />
              Reset workflow before run
            </label>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} loading={loading} disabled={workflows.length === 0}>
            Create Schedule
          </Button>
        </div>
      </div>
    </Modal>
  );
}
