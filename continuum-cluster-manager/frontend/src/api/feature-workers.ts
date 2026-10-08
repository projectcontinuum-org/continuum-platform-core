import type {
  FeatureWorkerCreateRequest,
  FeatureWorkerUpdateRequest,
  FeatureWorkerResponse,
} from '../types/api';
import { SERVICE_BASE } from '../basePath';

const API_BASE = `${SERVICE_BASE}/api/v1/feature-workers`;

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

export const featureWorkerApi = {
  /**
   * Create a new feature worker
   */
  async create(request: FeatureWorkerCreateRequest): Promise<FeatureWorkerResponse> {
    const response = await fetch(API_BASE, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(request),
    });
    return handleResponse<FeatureWorkerResponse>(response);
  },

  /**
   * Get the status of a specific feature worker
   */
  async getStatus(workerName: string): Promise<FeatureWorkerResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(workerName)}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<FeatureWorkerResponse>(response);
  },

  /**
   * List all feature workers (org-wide)
   */
  async list(): Promise<FeatureWorkerResponse[]> {
    const response = await fetch(API_BASE, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<FeatureWorkerResponse[]>(response);
  },

  /**
   * Delete a feature worker
   */
  async delete(workerName: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(workerName)}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    return handleResponse<void>(response);
  },

  /**
   * Update a feature worker's configuration
   */
  async update(workerName: string, request: FeatureWorkerUpdateRequest): Promise<FeatureWorkerResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(workerName)}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(request),
    });
    return handleResponse<FeatureWorkerResponse>(response);
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
