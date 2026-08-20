import { motion } from 'framer-motion';
import type { KnimeWorkflowResponse } from '../types/api';

interface WorkflowTableProps {
  workflows: KnimeWorkflowResponse[];
  onDownload: (workflow: KnimeWorkflowResponse) => void;
  onReplace: (workflow: KnimeWorkflowResponse) => void;
  onDelete: (workflow: KnimeWorkflowResponse) => void;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const fadeInUp = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
};

export function WorkflowTable({ workflows, onDownload, onReplace, onDelete }: WorkflowTableProps) {
  return (
    <>
      {/* Desktop Table */}
      <div className="hidden overflow-x-auto rounded-xl border border-divider bg-card md:block">
        <table className="w-full">
          <thead>
            <tr className="border-b border-divider bg-surface/50">
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">File Name</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Size</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Uploaded</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Updated</th>
              <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-fg-muted">Actions</th>
            </tr>
          </thead>
          <motion.tbody
            initial="hidden"
            animate="visible"
            variants={{ visible: { transition: { staggerChildren: 0.05 } } }}
          >
            {workflows.map((wf) => (
              <motion.tr
                key={wf.workflowId}
                variants={fadeInUp}
                className="border-b border-divider/50 transition-colors last:border-0 hover:bg-surface/30"
              >
                <td className="px-4 py-3">
                  <span className="font-medium text-fg">{wf.fileName}</span>
                </td>
                <td className="px-4 py-3 text-sm text-fg-muted">{formatSize(wf.sizeBytes)}</td>
                <td className="px-4 py-3 text-sm text-fg-muted">{formatDate(wf.createdAt)}</td>
                <td className="px-4 py-3 text-sm text-fg-muted">{formatDate(wf.updatedAt)}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center justify-end gap-1">
                    {/* Download */}
                    <button
                      onClick={() => onDownload(wf)}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-accent/10 hover:text-accent"
                      title="Download workflow"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                      </svg>
                    </button>
                    {/* Replace */}
                    <button
                      onClick={() => onReplace(wf)}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-surface hover:text-fg"
                      title="Replace file"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                      </svg>
                    </button>
                    {/* Delete */}
                    <button
                      onClick={() => onDelete(wf)}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-red-100 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400"
                      title="Delete workflow"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                </td>
              </motion.tr>
            ))}
          </motion.tbody>
        </table>
      </div>

      {/* Mobile Cards */}
      <div className="space-y-3 md:hidden">
        {workflows.map((wf) => (
          <motion.div
            key={wf.workflowId}
            variants={fadeInUp}
            initial="hidden"
            animate="visible"
            className="rounded-xl border border-divider bg-card p-4"
          >
            <h3 className="font-medium text-fg">{wf.fileName}</h3>

            <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-fg-muted">
              <div>
                <span className="font-medium">Size:</span> {formatSize(wf.sizeBytes)}
              </div>
              <div>
                <span className="font-medium">Uploaded:</span> {formatDate(wf.createdAt)}
              </div>
              <div className="col-span-2">
                <span className="font-medium">Updated:</span> {formatDate(wf.updatedAt)}
              </div>
            </div>

            <div className="mt-3 flex gap-2 border-t border-divider/50 pt-3">
              <button
                onClick={() => onDownload(wf)}
                className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-fg-muted transition-colors hover:bg-accent/10 hover:text-accent"
              >
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                </svg>
                Download
              </button>
              <button
                onClick={() => onReplace(wf)}
                className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-fg-muted transition-colors hover:bg-surface hover:text-fg"
              >
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                Replace
              </button>
              <button
                onClick={() => onDelete(wf)}
                className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-fg-muted transition-colors hover:bg-red-100 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400"
              >
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
                Delete
              </button>
            </div>
          </motion.div>
        ))}
      </div>
    </>
  );
}
