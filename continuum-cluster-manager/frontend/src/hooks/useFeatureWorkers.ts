import { useState, useCallback, useEffect } from 'react';
import type { FeatureWorkerResponse, FeatureWorkerCreateRequest, FeatureWorkerUpdateRequest } from '../types/api';
import { featureWorkerApi, ApiError } from '../api/feature-workers';

interface UseFeatureWorkersResult {
  featureWorkers: FeatureWorkerResponse[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  createFeatureWorker: (request: FeatureWorkerCreateRequest) => Promise<FeatureWorkerResponse>;
  deleteFeatureWorker: (workerName: string) => Promise<void>;
  updateFeatureWorker: (workerName: string, request: FeatureWorkerUpdateRequest) => Promise<FeatureWorkerResponse>;
}

export function useFeatureWorkers(): UseFeatureWorkersResult {
  const [featureWorkers, setFeatureWorkers] = useState<FeatureWorkerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await featureWorkerApi.list();
      // Filter out deleted workers and sort by createdAt descending
      const activeWorkers = data
        .filter(fw => fw.status !== 'DELETED')
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setFeatureWorkers(activeWorkers);
    } catch (err) {
      const message = err instanceof ApiError
        ? `API Error (${err.status}): ${err.message}`
        : err instanceof Error
          ? err.message
          : 'An unknown error occurred';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const createFeatureWorker = useCallback(async (request: FeatureWorkerCreateRequest) => {
    const result = await featureWorkerApi.create(request);
    await refresh();
    return result;
  }, [refresh]);

  const deleteFeatureWorker = useCallback(async (workerName: string) => {
    await featureWorkerApi.delete(workerName);
    await refresh();
  }, [refresh]);

  const updateFeatureWorker = useCallback(async (workerName: string, request: FeatureWorkerUpdateRequest) => {
    const result = await featureWorkerApi.update(workerName, request);
    await refresh();
    return result;
  }, [refresh]);

  return {
    featureWorkers,
    loading,
    error,
    refresh,
    createFeatureWorker,
    deleteFeatureWorker,
    updateFeatureWorker,
  };
}
