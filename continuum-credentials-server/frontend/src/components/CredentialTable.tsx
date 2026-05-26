import { motion } from 'framer-motion';
import type { CredentialResponse } from '../types/api';
import { TypeBadge } from './TypeBadge';

interface CredentialTableProps {
  credentials: CredentialResponse[];
  onView: (credential: CredentialResponse) => void;
  onEdit: (credential: CredentialResponse) => void;
  onDelete: (credential: CredentialResponse) => void;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

function formatDateTime(iso: string | null): string {
  if (!iso) return 'Never';
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

const fadeInUp = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
};

export function CredentialTable({ credentials, onView, onEdit, onDelete }: CredentialTableProps) {
  return (
    <>
      {/* Desktop Table */}
      <div className="hidden overflow-x-auto rounded-xl border border-divider bg-card md:block">
        <table className="w-full">
          <thead>
            <tr className="border-b border-divider bg-surface/50">
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Name</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Type</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Created By</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Created Date</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Last Used</th>
              <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-fg-muted">Actions</th>
            </tr>
          </thead>
          <motion.tbody
            initial="hidden"
            animate="visible"
            variants={{ visible: { transition: { staggerChildren: 0.05 } } }}
          >
            {credentials.map((cred) => (
              <motion.tr
                key={cred.name}
                variants={fadeInUp}
                className="border-b border-divider/50 transition-colors last:border-0 hover:bg-surface/30"
              >
                <td className="px-4 py-3">
                  <div className="flex flex-col">
                    <span className="font-medium text-fg">{cred.name}</span>
                    {cred.description && (
                      <span className="mt-0.5 text-xs text-fg-muted line-clamp-1">{cred.description}</span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <TypeBadge type={cred.type} />
                </td>
                <td className="px-4 py-3 text-sm text-fg-muted">{cred.createdBy}</td>
                <td className="px-4 py-3 text-sm text-fg-muted">{formatDate(cred.createdAt)}</td>
                <td className="px-4 py-3 text-sm text-fg-muted">{formatDateTime(cred.lastAccessedAt)}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center justify-end gap-1">
                    {/* View */}
                    <button
                      onClick={() => onView(cred)}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-accent/10 hover:text-accent"
                      title="View credential"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                      </svg>
                    </button>
                    {/* Edit */}
                    <button
                      onClick={() => onEdit(cred)}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-surface hover:text-fg"
                      title="Edit credential"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                      </svg>
                    </button>
                    {/* Delete */}
                    <button
                      onClick={() => onDelete(cred)}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-red-100 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400"
                      title="Delete credential"
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
        {credentials.map((cred) => (
          <motion.div
            key={cred.name}
            variants={fadeInUp}
            initial="hidden"
            animate="visible"
            className="rounded-xl border border-divider bg-card p-4"
          >
            <div className="flex items-start justify-between">
              <div>
                <h3 className="font-medium text-fg">{cred.name}</h3>
                {cred.description && (
                  <p className="mt-0.5 text-xs text-fg-muted line-clamp-2">{cred.description}</p>
                )}
              </div>
              <TypeBadge type={cred.type} />
            </div>

            <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-fg-muted">
              <div>
                <span className="font-medium">Created by:</span> {cred.createdBy}
              </div>
              <div>
                <span className="font-medium">Created:</span> {formatDate(cred.createdAt)}
              </div>
              <div className="col-span-2">
                <span className="font-medium">Last used:</span> {formatDateTime(cred.lastAccessedAt)}
              </div>
            </div>

            <div className="mt-3 flex gap-2 border-t border-divider/50 pt-3">
              <button
                onClick={() => onView(cred)}
                className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-fg-muted transition-colors hover:bg-accent/10 hover:text-accent"
              >
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                View
              </button>
              <button
                onClick={() => onEdit(cred)}
                className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-fg-muted transition-colors hover:bg-surface hover:text-fg"
              >
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
                Edit
              </button>
              <button
                onClick={() => onDelete(cred)}
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
