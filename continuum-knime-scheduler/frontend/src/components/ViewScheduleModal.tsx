import { Modal } from './Modal';
import { Button } from './Button';
import { StatusBadge } from './StatusBadge';
import type { KnimeWorkflowScheduleResponse } from '../types/api';

interface ViewScheduleModalProps {
  schedule: KnimeWorkflowScheduleResponse | null;
  workflowFileName: string | undefined;
  isOpen: boolean;
  onClose: () => void;
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-2">
      <span className="text-sm text-fg-muted">{label}</span>
      <span className="text-right text-sm text-fg">{children}</span>
    </div>
  );
}

export function ViewScheduleModal({ schedule, workflowFileName, isOpen, onClose }: ViewScheduleModalProps) {
  if (!schedule) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Schedule Details" size="lg">
      <div className="space-y-1 divide-y divide-divider/50">
        <DetailRow label="Name">{schedule.name}</DetailRow>
        <DetailRow label="Status">
          <StatusBadge paused={schedule.paused} />
        </DetailRow>
        <DetailRow label="Workflow file">{workflowFileName ?? 'Unknown workflow'}</DetailRow>
        <DetailRow label="Cron expression">
          <code>{schedule.cronExpression}</code>
        </DetailRow>
        <DetailRow label="Time zone">{schedule.timeZone ?? 'Default'}</DetailRow>
        <DetailRow label="Reset workflow before run">{schedule.resetWorkflow ? 'Yes' : 'No'}</DetailRow>
        <DetailRow label="Timeout">{schedule.timeoutSeconds}s</DetailRow>
        <DetailRow label="Owned by">{schedule.ownedBy}</DetailRow>
        <DetailRow label="Created">{formatDateTime(schedule.createdAt)}</DetailRow>
        <DetailRow label="Updated">{formatDateTime(schedule.updatedAt)}</DetailRow>
      </div>

      <div className="mt-4">
        <h3 className="mb-2 text-sm font-semibold text-fg">Upcoming Runs</h3>
        {schedule.nextRunTimes.length > 0 ? (
          <ul className="space-y-1 rounded-lg border border-divider bg-surface/30 p-3">
            {schedule.nextRunTimes.map((t) => (
              <li key={t} className="text-sm text-fg-muted">
                {formatDateTime(t)}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-fg-muted">No upcoming runs scheduled.</p>
        )}
      </div>

      {schedule.workflowVariables.length > 0 && (
        <div className="mt-4">
          <h3 className="mb-2 text-sm font-semibold text-fg">Workflow Variables</h3>
          <ul className="space-y-1 rounded-lg border border-divider bg-surface/30 p-3">
            {schedule.workflowVariables.map((v, i) => (
              <li key={i} className="text-sm text-fg-muted">
                <code>{v.name}</code> = <code>{v.value}</code> ({v.type})
              </li>
            ))}
          </ul>
        </div>
      )}

      {schedule.workflowCredentials.length > 0 && (
        <div className="mt-4">
          <h3 className="mb-2 text-sm font-semibold text-fg">Workflow Credentials</h3>
          <ul className="space-y-1 rounded-lg border border-divider bg-surface/30 p-3">
            {schedule.workflowCredentials.map((c, i) => (
              <li key={i} className="text-sm text-fg-muted">
                <code>{c.knimeCredentialName}</code> → <code>{c.credential}</code>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-6 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  );
}
