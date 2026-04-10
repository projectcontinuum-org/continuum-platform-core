import { useState, useCallback, useEffect } from 'react';
import type { WorkbenchResponse, WorkbenchCreateRequest, WorkbenchUpdateRequest } from '../types/api';
import { workbenchApi, ApiError } from '../api/workbench';

interface UseWorkbenchesResult {
  workbenches: WorkbenchResponse[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  createWorkbench: (request: WorkbenchCreateRequest) => Promise<WorkbenchResponse>;
  deleteWorkbench: (instanceName: string) => Promise<void>;
  suspendWorkbench: (instanceName: string) => Promise<WorkbenchResponse>;
  resumeWorkbench: (instanceName: string) => Promise<WorkbenchResponse>;
  updateWorkbench: (instanceName: string, request: WorkbenchUpdateRequest) => Promise<WorkbenchResponse>;
}

export function useWorkbenches(): UseWorkbenchesResult {
  const [workbenches, setWorkbenches] = useState<WorkbenchResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await workbenchApi.list();
      // Filter out deleted workbenches and sort by createdAt descending
      const activeWorkbenches = data
        .filter(wb => wb.status !== 'DELETED')
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setWorkbenches(activeWorkbenches);
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

  const createWorkbench = useCallback(async (request: WorkbenchCreateRequest) => {
    const result = await workbenchApi.create(request);
    await refresh();
    return result;
  }, [refresh]);

  const deleteWorkbench = useCallback(async (instanceName: string) => {
    await workbenchApi.delete(instanceName);
    await refresh();
  }, [refresh]);

  const suspendWorkbench = useCallback(async (instanceName: string) => {
    const result = await workbenchApi.suspend(instanceName);
    await refresh();
    return result;
  }, [refresh]);

  const resumeWorkbench = useCallback(async (instanceName: string) => {
    const result = await workbenchApi.resume(instanceName);
    await refresh();
    return result;
  }, [refresh]);

  const updateWorkbench = useCallback(async (instanceName: string, request: WorkbenchUpdateRequest) => {
    const result = await workbenchApi.update(instanceName, request);
    await refresh();
    return result;
  }, [refresh]);

  return {
    workbenches,
    loading,
    error,
    refresh,
    createWorkbench,
    deleteWorkbench,
    suspendWorkbench,
    resumeWorkbench,
    updateWorkbench,
  };
}

