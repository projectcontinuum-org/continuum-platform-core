import type {
  CredentialCreateRequest,
  CredentialUpdateRequest,
  CredentialResponse,
} from '../types/api';
import { SERVICE_BASE } from '../basePath';

const API_BASE = `${SERVICE_BASE}/api/v1/credentials`;

const getHeaders = (): HeadersInit => ({
  'Content-Type': 'application/json',
  // Note: x-continuum-user-id is injected by the boundary service (OAuth2 Proxy)
  // Do not set it here
});

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorText = await response.text();
    let message = errorText || `HTTP ${response.status}`;
    try {
      const errorJson = JSON.parse(errorText);
      if (errorJson.error) message = errorJson.error;
    } catch {
      // use raw text
    }
    throw new ApiError(response.status, message);
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export const credentialsApi = {
  async create(request: CredentialCreateRequest): Promise<CredentialResponse> {
    const response = await fetch(API_BASE, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(request),
    });
    return handleResponse<CredentialResponse>(response);
  },

  async list(): Promise<CredentialResponse[]> {
    const response = await fetch(API_BASE, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<CredentialResponse[]>(response);
  },

  async getByName(name: string): Promise<CredentialResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(name)}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<CredentialResponse>(response);
  },

  async update(name: string, request: CredentialUpdateRequest): Promise<CredentialResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(name)}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(request),
    });
    return handleResponse<CredentialResponse>(response);
  },

  async delete(name: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(name)}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    return handleResponse<void>(response);
  },
};
