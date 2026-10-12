import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { LoadingState } from './LoadingState';
import { ErrorState } from './ErrorState';
import { ExecutionStatusBadge } from './ExecutionStatusBadge';
import type { ToastNotification } from './Toast';
import { knimeWorkflowExecutionsApi } from '../api/knimeWorkflowExecutions';
import type { KnimeWorkflowExecutionResponse, KnimeWorkflowResponse } from '../types/api';

interface ExecutionsPanelProps {
  workflow: KnimeWorkflowResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onNotify: (type: ToastNotification['type'], message: string) => void;
}

const MIN_WIDTH = 320;
const DEFAULT_WIDTH = 420;
const CARD_LAYOUT_THRESHOLD = 480;
const PAGE_SIZE = 20;

function maxWidth(): number {
  return Math.min(720, window.innerWidth - 360);
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

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const fadeInUp = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
};

export function ExecutionsPanel({ workflow, isOpen, onClose, onNotify }: ExecutionsPanelProps) {
  const [executions, setExecutions] = useState<KnimeWorkflowExecutionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [width, setWidth] = useState(DEFAULT_WIDTH);
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia('(min-width: 1024px)').matches);
  const [contentWidth, setContentWidth] = useState(DEFAULT_WIDTH);

  const contentRef = useRef<HTMLDivElement | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const resizingRef = useRef(false);

  const loadExecutions = useCallback(async () => {
    if (!workflow) return;
    setLoading(true);
    setError(null);
    try {
      const result = await knimeWorkflowExecutionsApi.list(workflow.workflowId, 0, PAGE_SIZE);
      setExecutions(result.content);
      setPage(0);
      setHasMore(result.number + 1 < result.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load executions');
    } finally {
      setLoading(false);
    }
  }, [workflow]);

  const loadMore = useCallback(async () => {
    if (!workflow || loadingMore || !hasMore) return;
    setLoadingMore(true);
    try {
      const nextPage = page + 1;
      const result = await knimeWorkflowExecutionsApi.list(workflow.workflowId, nextPage, PAGE_SIZE);
      setExecutions((prev) => [...prev, ...result.content]);
      setPage(nextPage);
      setHasMore(result.number + 1 < result.totalPages);
    } catch (err) {
      onNotify('error', err instanceof Error ? err.message : 'Failed to load more executions');
    } finally {
      setLoadingMore(false);
    }
  }, [workflow, page, loadingMore, hasMore, onNotify]);

  useEffect(() => {
    if (isOpen && workflow) {
      loadExecutions();
    }
  }, [isOpen, workflow, loadExecutions]);

  // Track desktop vs. mobile layout mode.
  useEffect(() => {
    const query = window.matchMedia('(min-width: 1024px)');
    const handleChange = () => setIsDesktop(query.matches);
    query.addEventListener('change', handleChange);
    return () => query.removeEventListener('change', handleChange);
  }, []);

  // Measure the panel's own rendered width to decide table vs. card layout,
  // since that width is user-resizable on desktop and fluid on mobile.
  useEffect(() => {
    const el = contentRef.current;
    if (!el) return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) setContentWidth(entry.contentRect.width);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  // Load the next page once the sentinel at the bottom of the list scrolls into view.
  useEffect(() => {
    const root = scrollContainerRef.current;
    const target = sentinelRef.current;
    if (!root || !target) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadMore();
      },
      { root, rootMargin: '200px' }
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [loadMore]);

  // Drag-to-resize handling for the desktop split view.
  useEffect(() => {
    function handleMouseMove(e: MouseEvent) {
      if (!resizingRef.current) return;
      const next = window.innerWidth - e.clientX;
      setWidth(Math.min(maxWidth(), Math.max(MIN_WIDTH, next)));
    }
    function handleMouseUp() {
      if (resizingRef.current) {
        resizingRef.current = false;
        document.body.style.cursor = '';
      }
    }
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };
  }, []);

  const handleResizeStart = (e: React.MouseEvent) => {
    e.preventDefault();
    resizingRef.current = true;
    document.body.style.cursor = 'col-resize';
  };

  // Close on Escape; lock page scroll only for the mobile overlay drawer.
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      if (!isDesktop) document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = '';
    };
  }, [isOpen, isDesktop, onClose]);

  const handleDownload = async (execution: KnimeWorkflowExecutionResponse) => {
    if (!workflow) return;
    try {
      await knimeWorkflowExecutionsApi.download(workflow.workflowId, execution.executionId, execution.fileName);
    } catch (err) {
      onNotify('error', err instanceof Error ? err.message : 'Failed to download execution');
    }
  };

  if (!workflow) return null;

  const useCardLayout = contentWidth < CARD_LAYOUT_THRESHOLD;

  return (
    <>
      {/* Backdrop — mobile overlay only */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            key="executions-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-40 bg-overlay/50 backdrop-blur-sm lg:hidden"
          />
        )}
      </AnimatePresence>

      {/* Resize handle — desktop split view only */}
      {isOpen && (
        <div
          onMouseDown={handleResizeStart}
          className="hidden w-1.5 shrink-0 cursor-col-resize transition-colors hover:bg-accent/30 lg:block"
        />
      )}

      <AnimatePresence>
        {isOpen && (
          <motion.div
            key="executions-panel"
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ duration: 0.25 }}
            style={isDesktop ? { width } : undefined}
            ref={scrollContainerRef}
            className="fixed inset-y-0 right-0 z-50 w-full max-w-md overflow-y-auto border-l border-divider bg-card p-6 shadow-xl sm:max-w-lg lg:sticky lg:top-0 lg:z-auto lg:h-screen lg:w-auto lg:max-w-none lg:shrink-0 lg:shadow-none"
          >
            <div ref={contentRef}>
              {/* Header */}
              <div className="mb-4 flex items-center justify-between gap-4">
                <h2 className="truncate text-lg font-semibold text-fg">Executions — {workflow.fileName}</h2>
                <button
                  onClick={onClose}
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-surface hover:text-fg"
                >
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>

              {loading ? (
                <LoadingState label="Loading executions..." />
              ) : error ? (
                <ErrorState message={error} onRetry={loadExecutions} />
              ) : executions.length === 0 ? (
                <p className="py-8 text-center text-sm text-fg-muted">No executions yet.</p>
              ) : useCardLayout ? (
                <div className="space-y-3">
                  {executions.map((execution) => (
                    <motion.div
                      key={execution.executionId}
                      variants={fadeInUp}
                      initial="hidden"
                      animate="visible"
                      className="rounded-xl border border-divider bg-surface/30 p-4"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <h3 className="break-all font-medium text-fg">{execution.fileName}</h3>
                        <ExecutionStatusBadge status={execution.status} />
                      </div>

                      <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-fg-muted">
                        <div>
                          <span className="font-medium">Size:</span> {formatSize(execution.sizeBytes)}
                        </div>
                        <div className="col-span-2">
                          <span className="font-medium">Created:</span> {formatDateTime(execution.createdAt)}
                        </div>
                      </div>

                      <div className="mt-3 flex gap-2 border-t border-divider/50 pt-3">
                        <button
                          onClick={() => handleDownload(execution)}
                          className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-fg-muted transition-colors hover:bg-accent/10 hover:text-accent"
                        >
                          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                          </svg>
                          Download
                        </button>
                      </div>
                    </motion.div>
                  ))}
                </div>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-divider">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b border-divider bg-surface/50">
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">File Name</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Status</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Size</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-fg-muted">Created</th>
                        <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-fg-muted">Actions</th>
                      </tr>
                    </thead>
                    <motion.tbody
                      initial="hidden"
                      animate="visible"
                      variants={{ visible: { transition: { staggerChildren: 0.05 } } }}
                    >
                      {executions.map((execution) => (
                        <motion.tr
                          key={execution.executionId}
                          variants={fadeInUp}
                          className="border-b border-divider/50 transition-colors last:border-0 hover:bg-surface/30"
                        >
                          <td className="px-4 py-3">
                            <span className="font-medium text-fg">{execution.fileName}</span>
                          </td>
                          <td className="px-4 py-3">
                            <ExecutionStatusBadge status={execution.status} />
                          </td>
                          <td className="px-4 py-3 text-sm text-fg-muted">{formatSize(execution.sizeBytes)}</td>
                          <td className="px-4 py-3 text-sm text-fg-muted">{formatDateTime(execution.createdAt)}</td>
                          <td className="px-4 py-3">
                            <div className="flex items-center justify-end">
                              <button
                                onClick={() => handleDownload(execution)}
                                className="flex h-8 w-8 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-accent/10 hover:text-accent"
                                title="Download execution"
                              >
                                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                </svg>
                              </button>
                            </div>
                          </td>
                        </motion.tr>
                      ))}
                    </motion.tbody>
                  </table>
                </div>
              )}

              {hasMore && !loading && !error && executions.length > 0 && (
                <div ref={sentinelRef} className="py-4 text-center text-xs text-fg-muted">
                  {loadingMore ? 'Loading more…' : ''}
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
