import type {
  WorkbenchCreateRequest,
  WorkbenchUpdateRequest,
  WorkbenchResponse,
  WorkbenchLivenessResponse,
  DockerHubTag,
} from '../types/api';
import { SERVICE_BASE } from '../basePath';

const API_BASE = `${SERVICE_BASE}/api/v1/workbench`;

const getHeaders = (): HeadersInit => ({
  'Content-Type': 'application/json',
  // Note: x-continuum-user-id is injected by the boundary service (OAuth2 Proxy)
  // Do not set it here
});

class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorText = await response.text();
    throw new ApiError(response.status, errorText || `HTTP ${response.status}`);
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export const workbenchApi = {
  /**
   * Create a new workbench instance
   */
  async create(request: WorkbenchCreateRequest): Promise<WorkbenchResponse> {
    const response = await fetch(API_BASE, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(request),
    });
    return handleResponse<WorkbenchResponse>(response);
  },

  /**
   * Get the status of a specific workbench
   */
  async getStatus(instanceName: string): Promise<WorkbenchResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(instanceName)}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<WorkbenchResponse>(response);
  },

  /**
   * List all workbenches for the current user
   */
  async list(): Promise<WorkbenchResponse[]> {
    const response = await fetch(API_BASE, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<WorkbenchResponse[]>(response);
  },

  /**
   * Delete a workbench
   */
  async delete(instanceName: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(instanceName)}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    return handleResponse<void>(response);
  },

  /**
   * Suspend a workbench (keep data, stop compute)
   */
  async suspend(instanceName: string): Promise<WorkbenchResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(instanceName)}/suspend`, {
      method: 'PUT',
      headers: getHeaders(),
    });
    return handleResponse<WorkbenchResponse>(response);
  },

  /**
   * Resume a suspended workbench
   */
  async resume(instanceName: string): Promise<WorkbenchResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(instanceName)}/resume`, {
      method: 'PUT',
      headers: getHeaders(),
    });
    return handleResponse<WorkbenchResponse>(response);
  },

  /**
   * Update a workbench's configuration
   */
  async update(instanceName: string, request: WorkbenchUpdateRequest): Promise<WorkbenchResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(instanceName)}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(request),
    });
    return handleResponse<WorkbenchResponse>(response);
  },

  /**
   * Check if the workbench is ready to accept connections, via
   * cluster-manager's own liveness endpoint (backed by K8s deployment readiness).
   */
  async checkReady(instanceName: string): Promise<boolean> {
    try {
      const response = await fetch(
        `${API_BASE}/${encodeURIComponent(instanceName)}/liveness`,
        { method: 'GET', headers: getHeaders() },
      );
      if (!response.ok) {
        return false;
      }
      const data: WorkbenchLivenessResponse = await response.json();
      return data.ready === true;
    } catch {
      return false;
    }
  },

  /**
   * Fetch available Docker Hub image tags for the workbench image
   */
  async getAvailableTags(): Promise<DockerHubTag[]> {
    const response = await fetch(`${API_BASE}/tags`, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<DockerHubTag[]>(response);
  },

  /**
   * Fetch available overlay variant names
   */
  async getAvailableVariants(): Promise<string[]> {
    const response = await fetch(`${API_BASE}/variants`, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<string[]>(response);
  },
};

export { ApiError };

