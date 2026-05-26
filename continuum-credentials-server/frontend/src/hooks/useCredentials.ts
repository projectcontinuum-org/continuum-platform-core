import { useState, useEffect, useCallback } from 'react';
import type { CredentialResponse, CredentialCreateRequest, CredentialUpdateRequest } from '../types/api';
import { credentialsApi } from '../api/credentials';

export function useCredentials() {
  const [credentials, setCredentials] = useState<CredentialResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await credentialsApi.list();
      // Sort by createdAt descending (newest first)
      data.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setCredentials(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load credentials');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const createCredential = useCallback(async (request: CredentialCreateRequest) => {
    await credentialsApi.create(request);
    await refresh();
  }, [refresh]);

  const updateCredential = useCallback(async (name: string, request: CredentialUpdateRequest) => {
    await credentialsApi.update(name, request);
    await refresh();
  }, [refresh]);

  const deleteCredential = useCallback(async (name: string) => {
    await credentialsApi.delete(name);
    await refresh();
  }, [refresh]);

  return {
    credentials,
    loading,
    error,
    refresh,
    createCredential,
    updateCredential,
    deleteCredential,
  };
}
