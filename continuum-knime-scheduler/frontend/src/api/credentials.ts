import type { CredentialSummary } from '../types/api';
import { SERVICE_BASE, handleResponse } from './client';

const API_BASE = `${SERVICE_BASE}/api/v1/credentials`;

export const credentialsApi = {
  async list(): Promise<CredentialSummary[]> {
    const response = await fetch(API_BASE, {
      method: 'GET',
    });
    return handleResponse<CredentialSummary[]>(response);
  },
};
