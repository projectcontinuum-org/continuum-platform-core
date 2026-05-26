import { useState, useCallback, useMemo } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';
import { useCredentials } from '../hooks/useCredentials';
import { useCredentialTypes } from '../hooks/useCredentialTypes';
import {
  Header,
  Footer,
  Button,
  EmptyState,
  LoadingState,
  ErrorState,
  CredentialTable,
  CreateCredentialModal,
  EditCredentialModal,
  DeleteConfirmModal,
  ViewCredentialModal,
  TypeBadge,
} from '../components';
import type { CredentialResponse } from '../types/api';

const fadeInUp = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

export function CredentialListPage() {
  const reducedMotion = useReducedMotion() ?? false;
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [viewingCredential, setViewingCredential] = useState<CredentialResponse | null>(null);
  const [editingCredential, setEditingCredential] = useState<CredentialResponse | null>(null);
  const [deletingCredential, setDeletingCredential] = useState<CredentialResponse | null>(null);
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const {
    credentials,
    loading,
    error,
    refresh,
    createCredential,
    updateCredential,
    deleteCredential,
  } = useCredentials();

  const {
    types: credentialTypes,
    uniqueTypeNames,
    getTypeWithVersion,
  } = useCredentialTypes();

  const showNotification = useCallback((type: 'success' | 'error', message: string) => {
    setNotification({ type, message });
    setTimeout(() => setNotification(null), 5000);
  }, []);

  const handleCreate = async (request: Parameters<typeof createCredential>[0]) => {
    try {
      await createCredential(request);
      showNotification('success', `Credential "${request.name}" created successfully`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to create credential');
      throw err;
    }
  };

  const handleUpdate = async (name: string, request: Parameters<typeof updateCredential>[1]) => {
    try {
      await updateCredential(name, request);
      showNotification('success', `Credential "${name}" updated successfully`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to update credential');
      throw err;
    }
  };

  const handleDelete = async (name: string) => {
    try {
      await deleteCredential(name);
      showNotification('success', `Credential "${name}" deleted successfully`);
    } catch (err) {
      showNotification('error', err instanceof Error ? err.message : 'Failed to delete credential');
      throw err;
    }
  };

  // Count credentials by type
  const typeCounts = credentials.reduce<Record<string, number>>((acc, cred) => {
    acc[cred.type] = (acc[cred.type] ?? 0) + 1;
    return acc;
  }, {});

  // Filter credentials by search query (name, type, description, createdBy)
  const filteredCredentials = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return credentials;
    return credentials.filter(
      (cred) =>
        cred.name.toLowerCase().includes(q) ||
        cred.type.toLowerCase().includes(q) ||
        cred.createdBy.toLowerCase().includes(q) ||
        (cred.description?.toLowerCase().includes(q) ?? false),
    );
  }, [credentials, searchQuery]);

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
                <span className="text-gradient">Credentials</span>
              </h1>
              <p className="mt-3 max-w-2xl text-fg-muted">
                Securely store and manage credentials for external services. All credentials are
                encrypted at rest and access-controlled per user.
              </p>
            </motion.div>

            {/* Stats */}
            {!loading && !error && credentials.length > 0 && (
              <motion.div
                variants={fadeInUp}
                initial="hidden"
                animate="visible"
                transition={{ duration: reducedMotion ? 0 : 0.5, delay: 0.1 }}
                className="mt-6 flex flex-wrap gap-4"
              >
                <div className="flex items-center gap-2">
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-accent/10 text-accent text-sm font-semibold">
                    {credentials.length}
                  </span>
                  <span className="text-sm text-fg-muted">Total</span>
                </div>
                {Object.entries(typeCounts).map(([type, count]) => (
                  <div key={type} className="flex items-center gap-2">
                    <TypeBadge type={type} />
                    <span className="text-sm text-fg-muted">{count}</span>
                  </div>
                ))}
              </motion.div>
            )}
          </div>
        </section>

        {/* Credentials List */}
        <section className="py-8">
          <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
            {/* Actions Bar */}
            <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-3">
                <Button onClick={() => setShowCreateModal(true)}>
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                  </svg>
                  New Credential
                </Button>
                <Button variant="ghost" onClick={refresh} disabled={loading}>
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                  </svg>
                  Refresh
                </Button>
              </div>

              {/* Search */}
              {!loading && !error && credentials.length > 0 && (
                <div className="relative">
                  <svg
                    className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-muted"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search by name or type..."
                    className="w-full rounded-lg border border-divider bg-base py-2 pl-9 pr-3 text-sm text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent sm:w-64"
                  />
                  {searchQuery && (
                    <button
                      onClick={() => setSearchQuery('')}
                      className="absolute right-2 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded text-fg-muted hover:text-fg"
                    >
                      <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* Content */}
            {loading && <LoadingState />}

            {error && <ErrorState message={error} onRetry={refresh} />}

            {!loading && !error && credentials.length === 0 && (
              <EmptyState onCreateClick={() => setShowCreateModal(true)} />
            )}

            {!loading && !error && credentials.length > 0 && (
              <>
                {filteredCredentials.length > 0 ? (
                  <CredentialTable
                    credentials={filteredCredentials}
                    onView={(cred) => setViewingCredential(cred)}
                    onEdit={(cred) => setEditingCredential(cred)}
                    onDelete={(cred) => setDeletingCredential(cred)}
                  />
                ) : (
                  <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-divider bg-surface/30 px-6 py-12">
                    <svg className="mb-3 h-10 w-10 text-fg-muted/50" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                    <p className="text-sm text-fg-muted">
                      No credentials matching "<span className="font-medium text-fg">{searchQuery}</span>"
                    </p>
                    <button
                      onClick={() => setSearchQuery('')}
                      className="mt-2 text-sm font-medium text-accent hover:text-highlight"
                    >
                      Clear search
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        </section>
      </main>

      <Footer />

      {/* Create Modal */}
      <CreateCredentialModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreate={handleCreate}
        credentialTypes={credentialTypes}
        uniqueTypeNames={uniqueTypeNames}
        getTypeWithVersion={getTypeWithVersion}
      />

      {/* Edit Modal */}
      <EditCredentialModal
        credential={editingCredential}
        isOpen={editingCredential !== null}
        onClose={() => setEditingCredential(null)}
        onUpdate={handleUpdate}
        credentialTypes={credentialTypes}
        getTypeWithVersion={getTypeWithVersion}
      />

      {/* Delete Confirm Modal */}
      <DeleteConfirmModal
        credential={deletingCredential}
        isOpen={deletingCredential !== null}
        onClose={() => setDeletingCredential(null)}
        onDelete={handleDelete}
      />

      {/* View Modal */}
      <ViewCredentialModal
        credential={viewingCredential}
        isOpen={viewingCredential !== null}
        onClose={() => setViewingCredential(null)}
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
