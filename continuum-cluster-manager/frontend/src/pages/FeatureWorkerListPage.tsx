import { useState, useCallback } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';
import { useFeatureWorkers } from '../hooks/useFeatureWorkers';
import {
  Header,
  Footer,
  Button,
  FeatureWorkerCard,
  CreateFeatureWorkerModal,
  EmptyState,
  LoadingState,
  ErrorState,
} from '../components';

const fadeInUp = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

export function FeatureWorkerListPage() {
  const reducedMotion = useReducedMotion() ?? false;
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const {
    featureWorkers,
    loading,
    error,
    refresh,
    createFeatureWorker,
    deleteFeatureWorker,
  } = useFeatureWorkers();

  const showNotification = useCallback((type: 'success' | 'error', message: string) => {
    setNotification({ type, message });
    setTimeout(() => setNotification(null), 5000);
  }, []);

  const handleCreate = async (request: Parameters<typeof createFeatureWorker>[0]) => {
    try {
      await createFeatureWorker(request);
      showNotification('success', `Feature worker "${request.workerName}" created successfully`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to create feature worker');
      throw err;
    }
  };

  const handleDelete = async (workerName: string) => {
    try {
      await deleteFeatureWorker(workerName);
      showNotification('success', `Feature worker "${workerName}" deleted`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to delete feature worker');
    }
  };

  const runningCount = featureWorkers.filter(fw => fw.status === 'RUNNING').length;

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
                <span className="text-gradient">Feature Worker Manager</span>
              </h1>
              <p className="mt-3 max-w-2xl text-fg-muted">
                Create and manage self-service Temporal feature workers. Each worker polls a dedicated
                task queue and processes workflow nodes independently of the DevOps deployment process.
              </p>
            </motion.div>

            {/* Stats */}
            {!loading && !error && featureWorkers.length > 0 && (
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
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-accent/10 text-accent">
                    {featureWorkers.length}
                  </span>
                  <span className="text-sm text-fg-muted">Total</span>
                </div>
              </motion.div>
            )}
          </div>
        </section>

        {/* Feature Worker Grid */}
        <section className="py-8">
          <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
            {/* Actions Bar */}
            <div className="mb-6 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Button onClick={() => setShowCreateModal(true)}>
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                  </svg>
                  New Feature Worker
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
            {loading && <LoadingState message="Loading feature workers..." />}

            {error && <ErrorState message={error} onRetry={refresh} title="Failed to Load Feature Workers" />}

            {!loading && !error && featureWorkers.length === 0 && (
              <EmptyState
                onCreateClick={() => setShowCreateModal(true)}
                title="No Feature Workers Yet"
                description="Create your first feature worker to start processing Temporal tasks. Each worker is an isolated deployment with its own task queue."
                createLabel="Create Your First Feature Worker"
              />
            )}

            {!loading && !error && featureWorkers.length > 0 && (
              <motion.div
                className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3"
                initial="hidden"
                animate="visible"
                variants={{
                  visible: { transition: { staggerChildren: 0.1 } },
                }}
              >
                <AnimatePresence mode="popLayout">
                  {featureWorkers.map((featureWorker) => (
                    <FeatureWorkerCard
                      key={featureWorker.workerId}
                      featureWorker={featureWorker}
                      onDelete={handleDelete}
                    />
                  ))}
                </AnimatePresence>
              </motion.div>
            )}
          </div>
        </section>
      </main>

      <Footer title="Continuum Feature Manager" />

      {/* Create Modal */}
      <CreateFeatureWorkerModal
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
