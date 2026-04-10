import { useState, useCallback } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';
import { useWorkbenches } from '../hooks/useWorkbenches';
import {
  Header,
  Footer,
  Button,
  WorkbenchCard,
  CreateWorkbenchModal,
  EmptyState,
  LoadingState,
  ErrorState,
} from '../components';
import type { WorkbenchResponse } from '../types/api';

const fadeInUp = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

export function WorkbenchListPage() {
  const reducedMotion = useReducedMotion() ?? false;
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const {
    workbenches,
    loading,
    error,
    refresh,
    createWorkbench,
    deleteWorkbench,
    suspendWorkbench,
    resumeWorkbench,
  } = useWorkbenches();

  const showNotification = useCallback((type: 'success' | 'error', message: string) => {
    setNotification({ type, message });
    setTimeout(() => setNotification(null), 5000);
  }, []);

  const handleCreate = async (request: Parameters<typeof createWorkbench>[0]) => {
    try {
      await createWorkbench(request);
      showNotification('success', `Workbench "${request.instanceName}" created successfully`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to create workbench');
      throw err;
    }
  };

  const handleSuspend = async (instanceName: string) => {
    try {
      await suspendWorkbench(instanceName);
      showNotification('success', `Workbench "${instanceName}" suspended`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to suspend workbench');
    }
  };

  const handleResume = async (instanceName: string) => {
    try {
      await resumeWorkbench(instanceName);
      showNotification('success', `Workbench "${instanceName}" resumed`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to resume workbench');
    }
  };

  const handleDelete = async (instanceName: string) => {
    try {
      await deleteWorkbench(instanceName);
      showNotification('success', `Workbench "${instanceName}" deleted`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to delete workbench');
    }
  };

  const handleOpen = (workbench: WorkbenchResponse) => {
    if (workbench.serviceEndpoint) {
      // Open workbench in a new tab via the cluster-manager reverse proxy
      const externalUrl = `/workbench/${workbench.instanceName}/open/#/workspace`;
      window.open(externalUrl, '_blank');
    }
  };

  const runningCount = workbenches.filter(wb => wb.status === 'RUNNING').length;
  const suspendedCount = workbenches.filter(wb => wb.status === 'SUSPENDED').length;

  return (
    <div className="flex min-h-screen flex-col bg-base">
      <Header />

      <main className="flex-1">
        {/* Hero Section */}
        <section className="border-b border-divider bg-gradient-to-b from-surface/50 to-base py-12">
          <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
            <motion.div
              variants={fadeInUp}
              initial="hidden"
              animate="visible"
              transition={{ duration: reducedMotion ? 0 : 0.5 }}
            >
              <h1 className="text-3xl font-bold sm:text-4xl">
                <span className="text-gradient">Workbench Manager</span>
              </h1>
              <p className="mt-3 max-w-2xl text-fg-muted">
                Create and manage your Continuum workbench instances. Each workbench is an isolated
                environment with persistent storage for building visual workflows.
              </p>
            </motion.div>

            {/* Stats */}
            {!loading && !error && workbenches.length > 0 && (
              <motion.div
                variants={fadeInUp}
                initial="hidden"
                animate="visible"
                transition={{ duration: reducedMotion ? 0 : 0.5, delay: 0.1 }}
                className="mt-6 flex flex-wrap gap-6"
              >
                <div className="flex items-center gap-2">
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400">
                    {runningCount}
                  </span>
                  <span className="text-sm text-fg-muted">Running</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400">
                    {suspendedCount}
                  </span>
                  <span className="text-sm text-fg-muted">Suspended</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-accent/10 text-accent">
                    {workbenches.length}
                  </span>
                  <span className="text-sm text-fg-muted">Total</span>
                </div>
              </motion.div>
            )}
          </div>
        </section>

        {/* Workbench Grid */}
        <section className="py-8">
          <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
            {/* Actions Bar */}
            <div className="mb-6 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Button onClick={() => setShowCreateModal(true)}>
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                  </svg>
                  New Workbench
                </Button>
                <Button variant="ghost" onClick={refresh} disabled={loading}>
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                  </svg>
                  Refresh
                </Button>
              </div>
            </div>

            {/* Content */}
            {loading && <LoadingState />}

            {error && <ErrorState message={error} onRetry={refresh} />}

            {!loading && !error && workbenches.length === 0 && (
              <EmptyState onCreateClick={() => setShowCreateModal(true)} />
            )}

            {!loading && !error && workbenches.length > 0 && (
              <motion.div
                className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3"
                initial="hidden"
                animate="visible"
                variants={{
                  visible: { transition: { staggerChildren: 0.1 } },
                }}
              >
                <AnimatePresence mode="popLayout">
                  {workbenches.map((workbench) => (
                    <WorkbenchCard
                      key={workbench.instanceId}
                      workbench={workbench}
                      onSuspend={handleSuspend}
                      onResume={handleResume}
                      onDelete={handleDelete}
                      onOpen={handleOpen}
                    />
                  ))}
                </AnimatePresence>
              </motion.div>
            )}
          </div>
        </section>
      </main>

      <Footer />

      {/* Create Modal */}
      <CreateWorkbenchModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreate={handleCreate}
      />

      {/* Notification Toast */}
      <AnimatePresence>
        {notification && (
          <motion.div
            initial={{ opacity: 0, y: 50, x: '-50%' }}
            animate={{ opacity: 1, y: 0, x: '-50%' }}
            exit={{ opacity: 0, y: 50, x: '-50%' }}
            className={`fixed bottom-6 left-1/2 z-50 flex items-center gap-3 rounded-lg px-4 py-3 shadow-lg ${
              notification.type === 'success'
                ? 'bg-green-600 text-white'
                : 'bg-red-600 text-white'
            }`}
          >
            {notification.type === 'success' ? (
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
              </svg>
            ) : (
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            )}
            <span className="text-sm font-medium">{notification.message}</span>
            <button
              onClick={() => setNotification(null)}
              className="ml-2 rounded p-1 hover:bg-white/20"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

