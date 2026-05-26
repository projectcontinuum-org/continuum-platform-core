import type { CredentialTypeResponse } from '../types/api';
import { SERVICE_BASE } from '../basePath';

const API_BASE = `${SERVICE_BASE}/api/v1/credential-types`;

const getHeaders = (): HeadersInit => ({
  'Content-Type': 'application/json',
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
  return response.json();
}

export const credentialTypesApi = {
  async listTypes(): Promise<CredentialTypeResponse[]> {
    const response = await fetch(API_BASE, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<CredentialTypeResponse[]>(response);
  },

  async getType(type: string): Promise<CredentialTypeResponse[]> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(type)}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<CredentialTypeResponse[]>(response);
  },

  async getTypeVersion(type: string, version: string): Promise<CredentialTypeResponse> {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(type)}/${encodeURIComponent(version)}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    return handleResponse<CredentialTypeResponse>(response);
  },
};
