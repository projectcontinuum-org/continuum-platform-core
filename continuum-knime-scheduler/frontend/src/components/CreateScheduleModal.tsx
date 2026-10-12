import { useState, useEffect } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { credentialsApi } from '../api/credentials';
import type {
  KnimeWorkflowResponse,
  CreateKnimeWorkflowScheduleRequest,
  KnimeWorkflowVariable,
  KnimeWorkflowCredentialRef,
  CredentialSummary,
} from '../types/api';

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
  workflowVariables: [],
  workflowCredentials: [],
};

const inputClass =
  'w-full rounded-lg border border-divider bg-surface px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none';

const errorClass = (submitAttempted: boolean, invalid: boolean) =>
  submitAttempted && invalid ? 'border-red-500 ring-1 ring-red-500' : '';

export function CreateScheduleModal({ isOpen, onClose, workflows, onCreate }: CreateScheduleModalProps) {
  const [form, setForm] = useState<CreateKnimeWorkflowScheduleRequest>(emptyForm);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [availableCredentials, setAvailableCredentials] = useState<CredentialSummary[]>([]);

  useEffect(() => {
    if (isOpen) {
      setForm({ ...emptyForm, knimeWorkflowId: workflows[0]?.workflowId ?? '' });
      setError(null);
      setSubmitAttempted(false);
      credentialsApi
        .list()
        .then(setAvailableCredentials)
        .catch((err) => {
          setAvailableCredentials([]);
          setError(err instanceof Error ? err.message : 'Failed to load credentials');
        });
    }
  }, [isOpen, workflows]);

  const isVariableRowIncomplete = (variable: KnimeWorkflowVariable) =>
    !variable.name.trim() || !variable.value.trim();

  const isCredentialRowIncomplete = (credential: KnimeWorkflowCredentialRef) =>
    !credential.knimeCredentialName.trim() || !credential.credential.trim();

  const handleSubmit = async () => {
    const hasIncompleteVariable = form.workflowVariables.some(isVariableRowIncomplete);
    const hasIncompleteCredential = form.workflowCredentials.some(isCredentialRowIncomplete);

    if (
      !form.name.trim() ||
      !form.cronExpression.trim() ||
      !form.knimeWorkflowId ||
      hasIncompleteVariable ||
      hasIncompleteCredential
    ) {
      setSubmitAttempted(true);
      setError(
        hasIncompleteVariable || hasIncompleteCredential
          ? 'Fill in all fields for each workflow variable and workflow credential row, or remove incomplete rows.'
          : 'Name, cron expression, and workflow are required'
      );
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await onCreate({
        ...form,
        timeZone: form.timeZone?.trim() ? form.timeZone.trim() : undefined,
        workflowVariables: form.workflowVariables.map((v) => ({ ...v, name: v.name.trim(), value: v.value.trim() })),
        workflowCredentials: form.workflowCredentials.map((c) => ({
          ...c,
          knimeCredentialName: c.knimeCredentialName.trim(),
          credential: c.credential.trim(),
        })),
      });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create schedule');
    } finally {
      setLoading(false);
    }
  };

  const addVariable = () =>
    setForm((f) => ({
      ...f,
      workflowVariables: [...f.workflowVariables, { name: '', value: '', type: 'String' }],
    }));

  const updateVariable = (index: number, field: keyof KnimeWorkflowVariable, value: string) =>
    setForm((f) => ({
      ...f,
      workflowVariables: f.workflowVariables.map((v, i) => (i === index ? { ...v, [field]: value } : v)),
    }));

  const removeVariable = (index: number) =>
    setForm((f) => ({
      ...f,
      workflowVariables: f.workflowVariables.filter((_, i) => i !== index),
    }));

  const addCredential = () =>
    setForm((f) => ({
      ...f,
      workflowCredentials: [...f.workflowCredentials, { knimeCredentialName: '', credential: '' }],
    }));

  const updateCredential = (index: number, field: keyof KnimeWorkflowCredentialRef, value: string) =>
    setForm((f) => ({
      ...f,
      workflowCredentials: f.workflowCredentials.map((c, i) => (i === index ? { ...c, [field]: value } : c)),
    }));

  const removeCredential = (index: number) =>
    setForm((f) => ({
      ...f,
      workflowCredentials: f.workflowCredentials.filter((_, i) => i !== index),
    }));

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Create Schedule" size="lg">
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
            className={`${inputClass} ${errorClass(submitAttempted, !form.name.trim())}`}
            placeholder="Nightly ETL run"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-fg">Workflow</label>
          <select
            value={form.knimeWorkflowId}
            onChange={(e) => setForm((f) => ({ ...f, knimeWorkflowId: e.target.value }))}
            className={`${inputClass} ${errorClass(submitAttempted, !form.knimeWorkflowId)}`}
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
              className={`${inputClass} ${errorClass(submitAttempted, !form.cronExpression.trim())}`}
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

        <div>
          <label className="mb-1 block text-sm font-medium text-fg">Workflow Variables (optional)</label>
          <div className="space-y-2 rounded-lg border border-divider p-3">
            {form.workflowVariables.map((variable, index) => (
              <div key={index} className="grid grid-cols-[1fr_1fr_110px_auto] items-center gap-2">
                <input
                  type="text"
                  value={variable.name}
                  onChange={(e) => updateVariable(index, 'name', e.target.value)}
                  className={`${inputClass} ${errorClass(submitAttempted, !variable.name.trim())}`}
                  placeholder="Name"
                />
                <input
                  type="text"
                  value={variable.value}
                  onChange={(e) => updateVariable(index, 'value', e.target.value)}
                  className={`${inputClass} ${errorClass(submitAttempted, !variable.value.trim())}`}
                  placeholder="Value"
                />
                <select
                  value={variable.type}
                  onChange={(e) => updateVariable(index, 'type', e.target.value)}
                  className={inputClass}
                >
                  <option value="String">String</option>
                  <option value="int">int</option>
                  <option value="double">double</option>
                </select>
                <Button type="button" variant="ghost" size="sm" onClick={() => removeVariable(index)}>
                  ×
                </Button>
              </div>
            ))}
            <Button type="button" variant="secondary" size="sm" onClick={addVariable}>
              + Add Variable
            </Button>
          </div>
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-fg">Workflow Credentials (optional)</label>
          <div className="space-y-2 rounded-lg border border-divider p-3">
            {form.workflowCredentials.map((credential, index) => (
              <div key={index} className="grid grid-cols-[1fr_1fr_auto] items-center gap-2">
                <input
                  type="text"
                  value={credential.knimeCredentialName}
                  onChange={(e) => updateCredential(index, 'knimeCredentialName', e.target.value)}
                  className={`${inputClass} ${errorClass(submitAttempted, !credential.knimeCredentialName.trim())}`}
                  placeholder="KNIME Credential Name"
                />
                <select
                  value={credential.credential}
                  onChange={(e) => updateCredential(index, 'credential', e.target.value)}
                  className={`${inputClass} ${errorClass(submitAttempted, !credential.credential.trim())}`}
                  disabled={availableCredentials.length === 0}
                >
                  <option value="">
                    {availableCredentials.length === 0 ? 'No credentials available' : 'Select a credential'}
                  </option>
                  {availableCredentials.map((c) => (
                    <option key={c.name} value={c.name}>
                      {c.name}
                    </option>
                  ))}
                </select>
                <Button type="button" variant="ghost" size="sm" onClick={() => removeCredential(index)}>
                  ×
                </Button>
              </div>
            ))}
            <Button type="button" variant="secondary" size="sm" onClick={addCredential}>
              + Add Credential
            </Button>
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
