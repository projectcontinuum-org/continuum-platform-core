import { useState, useEffect, useCallback, useMemo } from 'react';
import { AnimatePresence } from 'framer-motion';
import { Header } from '../components/Header';
import { Footer } from '../components/Footer';
import { Button } from '../components/Button';
import { EmptyState } from '../components/EmptyState';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { DeleteConfirmModal } from '../components/DeleteConfirmModal';
import { Toast, type ToastNotification } from '../components/Toast';
import { WorkflowTable } from '../components/WorkflowTable';
import { UploadWorkflowModal } from '../components/UploadWorkflowModal';
import { ReplaceWorkflowModal } from '../components/ReplaceWorkflowModal';
import { ScheduleCard } from '../components/ScheduleCard';
import { CreateScheduleModal } from '../components/CreateScheduleModal';
import { ViewScheduleModal } from '../components/ViewScheduleModal';
import { ExecutionsPanel } from '../components/ExecutionsPanel';
import { knimeWorkflowsApi } from '../api/knimeWorkflows';
import { knimeSchedulesApi } from '../api/knimeSchedules';
import type { KnimeWorkflowResponse, KnimeWorkflowScheduleResponse } from '../types/api';

type Tab = 'workflows' | 'schedules';

export function KnimeWorkflowsPage() {
  const [tab, setTab] = useState<Tab>('workflows');

  const [workflows, setWorkflows] = useState<KnimeWorkflowResponse[]>([]);
  const [schedules, setSchedules] = useState<KnimeWorkflowScheduleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState('');
  const [notification, setNotification] = useState<ToastNotification | null>(null);

  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [replaceTarget, setReplaceTarget] = useState<KnimeWorkflowResponse | null>(null);
  const [deleteWorkflowTarget, setDeleteWorkflowTarget] = useState<KnimeWorkflowResponse | null>(null);
  const [executionsWorkflow, setExecutionsWorkflow] = useState<KnimeWorkflowResponse | null>(null);

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [viewSchedule, setViewSchedule] = useState<KnimeWorkflowScheduleResponse | null>(null);
  const [deleteScheduleTarget, setDeleteScheduleTarget] = useState<KnimeWorkflowScheduleResponse | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [workflowPage, scheduleList] = await Promise.all([
        knimeWorkflowsApi.list(0, 100),
        knimeSchedulesApi.list(),
      ]);
      setWorkflows(workflowPage.content);
      setSchedules(scheduleList);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (!notification) return;
    const timer = setTimeout(() => setNotification(null), 4000);
    return () => clearTimeout(timer);
  }, [notification]);

  const workflowById = useMemo(() => {
    const map = new Map<string, KnimeWorkflowResponse>();
    workflows.forEach((wf) => map.set(wf.workflowId, wf));
    return map;
  }, [workflows]);

  const filteredWorkflows = useMemo(() => {
    if (!search.trim()) return workflows;
    const term = search.trim().toLowerCase();
    return workflows.filter((wf) => wf.fileName.toLowerCase().includes(term));
  }, [workflows, search]);

  const notify = (type: ToastNotification['type'], message: string) => setNotification({ type, message });

  const handleUpload = async (file: File) => {
    const created = await knimeWorkflowsApi.upload(file);
    setWorkflows((prev) => [created, ...prev]);
    notify('success', `Uploaded ${created.fileName}`);
  };

  const handleReplace = async (workflowId: string, file: File) => {
    const updated = await knimeWorkflowsApi.replace(workflowId, file);
    setWorkflows((prev) => prev.map((wf) => (wf.workflowId === workflowId ? updated : wf)));
    notify('success', `Replaced ${updated.fileName}`);
  };

  const handleDownload = async (workflow: KnimeWorkflowResponse) => {
    try {
      await knimeWorkflowsApi.download(workflow.workflowId, workflow.fileName);
    } catch (err) {
      notify('error', err instanceof Error ? err.message : 'Failed to download workflow');
    }
  };

  const handleDeleteWorkflow = async () => {
    if (!deleteWorkflowTarget) return;
    await knimeWorkflowsApi.remove(deleteWorkflowTarget.workflowId);
    setWorkflows((prev) => prev.filter((wf) => wf.workflowId !== deleteWorkflowTarget.workflowId));
    notify('success', `Deleted ${deleteWorkflowTarget.fileName}`);
  };

  const handleCreateSchedule = async (request: Parameters<typeof knimeSchedulesApi.create>[0]) => {
    const created = await knimeSchedulesApi.create(request);
    setSchedules((prev) => [created, ...prev]);
    notify('success', `Created schedule ${created.name}`);
  };

  const handlePause = async (scheduleId: string) => {
    try {
      await knimeSchedulesApi.pause(scheduleId);
      const updated = await knimeSchedulesApi.get(scheduleId);
      setSchedules((prev) => prev.map((s) => (s.scheduleId === scheduleId ? updated : s)));
      notify('success', `Paused ${updated.name}`);
    } catch (err) {
      notify('error', err instanceof Error ? err.message : 'Failed to pause schedule');
    }
  };

  const handleUnpause = async (scheduleId: string) => {
    try {
      await knimeSchedulesApi.unpause(scheduleId);
      const updated = await knimeSchedulesApi.get(scheduleId);
      setSchedules((prev) => prev.map((s) => (s.scheduleId === scheduleId ? updated : s)));
      notify('success', `Unpaused ${updated.name}`);
    } catch (err) {
      notify('error', err instanceof Error ? err.message : 'Failed to unpause schedule');
    }
  };

  const handleTrigger = async (scheduleId: string) => {
    try {
      await knimeSchedulesApi.trigger(scheduleId);
      notify('success', 'Triggered schedule run');
    } catch (err) {
      notify('error', err instanceof Error ? err.message : 'Failed to trigger schedule');
    }
  };

  const handleDeleteSchedule = async () => {
    if (!deleteScheduleTarget) return;
    await knimeSchedulesApi.remove(deleteScheduleTarget.scheduleId);
    setSchedules((prev) => prev.filter((s) => s.scheduleId !== deleteScheduleTarget.scheduleId));
    notify('success', `Deleted schedule ${deleteScheduleTarget.name}`);
  };

  return (
    <div className="flex min-h-screen bg-base">
      <div className="flex min-h-screen min-w-0 flex-1 flex-col">
        <Header />

        <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-4 py-10 sm:px-6 lg:px-8">
        {/* Hero */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-fg sm:text-4xl">
            <span className="text-gradient">KNIME Scheduler</span>
          </h1>
          <p className="mt-2 text-fg-muted">
            Upload KNIME workflow files and manage their run schedules.
          </p>
        </div>

        {/* Stats */}
        <div className="mb-8 grid grid-cols-2 gap-4 sm:grid-cols-2">
          <div className="rounded-xl border border-divider bg-card p-4">
            <p className="text-sm text-fg-muted">Workflow files</p>
            <p className="mt-1 text-2xl font-bold text-fg">{workflows.length}</p>
          </div>
          <div className="rounded-xl border border-divider bg-card p-4">
            <p className="text-sm text-fg-muted">Schedules</p>
            <p className="mt-1 text-2xl font-bold text-fg">{schedules.length}</p>
          </div>
        </div>

        {/* Tabs */}
        <div className="mb-6 flex gap-2 border-b border-divider">
          <button
            onClick={() => setTab('workflows')}
            className={`px-4 py-2 text-sm font-medium transition-colors ${
              tab === 'workflows'
                ? 'border-b-2 border-accent text-fg'
                : 'text-fg-muted hover:text-fg'
            }`}
          >
            Workflows
          </button>
          <button
            onClick={() => setTab('schedules')}
            className={`px-4 py-2 text-sm font-medium transition-colors ${
              tab === 'schedules'
                ? 'border-b-2 border-accent text-fg'
                : 'text-fg-muted hover:text-fg'
            }`}
          >
            Schedules
          </button>
        </div>

        {/* Actions bar */}
        <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          {tab === 'workflows' ? (
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by file name..."
              className="w-full rounded-lg border border-divider bg-surface px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none sm:max-w-xs"
            />
          ) : (
            <div />
          )}

          <Button onClick={() => (tab === 'workflows' ? setUploadModalOpen(true) : setCreateModalOpen(true))}>
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
            </svg>
            {tab === 'workflows' ? 'Upload Workflow' : 'Create Schedule'}
          </Button>
        </div>

        {/* Content */}
        {loading ? (
          <LoadingState label={tab === 'workflows' ? 'Loading workflows...' : 'Loading schedules...'} />
        ) : error ? (
          <ErrorState message={error} onRetry={loadData} />
        ) : tab === 'workflows' ? (
          filteredWorkflows.length === 0 ? (
            <EmptyState
              title={workflows.length === 0 ? 'No Workflows Yet' : 'No Matching Workflows'}
              description={
                workflows.length === 0
                  ? 'Upload a .knwf file to get started with scheduled runs.'
                  : 'Try a different search term.'
              }
              actionLabel="Upload Workflow"
              onActionClick={() => setUploadModalOpen(true)}
            />
          ) : (
            <WorkflowTable
              workflows={filteredWorkflows}
              onViewExecutions={setExecutionsWorkflow}
              onDownload={handleDownload}
              onReplace={setReplaceTarget}
              onDelete={setDeleteWorkflowTarget}
            />
          )
        ) : schedules.length === 0 ? (
          <EmptyState
            title="No Schedules Yet"
            description="Create a schedule to run one of your uploaded workflows automatically."
            actionLabel="Create Schedule"
            onActionClick={() => setCreateModalOpen(true)}
          />
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <AnimatePresence>
              {schedules.map((schedule) => (
                <ScheduleCard
                  key={schedule.scheduleId}
                  schedule={schedule}
                  workflowFileName={workflowById.get(schedule.knimeWorkflowId)?.fileName}
                  onPause={handlePause}
                  onUnpause={handleUnpause}
                  onTrigger={handleTrigger}
                  onView={setViewSchedule}
                  onDelete={setDeleteScheduleTarget}
                />
              ))}
            </AnimatePresence>
          </div>
        )}
      </main>

        <Footer />
      </div>

      <ExecutionsPanel
        workflow={executionsWorkflow}
        isOpen={executionsWorkflow !== null}
        onClose={() => setExecutionsWorkflow(null)}
        onNotify={notify}
      />

      {/* Workflow modals */}
      <UploadWorkflowModal
        isOpen={uploadModalOpen}
        onClose={() => setUploadModalOpen(false)}
        onUpload={handleUpload}
      />
      <ReplaceWorkflowModal
        workflow={replaceTarget}
        isOpen={replaceTarget !== null}
        onClose={() => setReplaceTarget(null)}
        onReplace={handleReplace}
      />
      <DeleteConfirmModal
        isOpen={deleteWorkflowTarget !== null}
        title="Delete Workflow File"
        itemName={deleteWorkflowTarget?.fileName ?? ''}
        warning="This will permanently delete the workflow file. Any schedules referencing it will fail on their next run."
        onClose={() => setDeleteWorkflowTarget(null)}
        onDelete={handleDeleteWorkflow}
      />

      {/* Schedule modals */}
      <CreateScheduleModal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        workflows={workflows}
        onCreate={handleCreateSchedule}
      />
      <ViewScheduleModal
        schedule={viewSchedule}
        workflowFileName={viewSchedule ? workflowById.get(viewSchedule.knimeWorkflowId)?.fileName : undefined}
        isOpen={viewSchedule !== null}
        onClose={() => setViewSchedule(null)}
      />
      <DeleteConfirmModal
        isOpen={deleteScheduleTarget !== null}
        title="Delete Schedule"
        itemName={deleteScheduleTarget?.name ?? ''}
        warning="This will permanently delete the schedule. Future runs will no longer be triggered."
        onClose={() => setDeleteScheduleTarget(null)}
        onDelete={handleDeleteSchedule}
      />

      <Toast notification={notification} onDismiss={() => setNotification(null)} />
    </div>
  );
}
