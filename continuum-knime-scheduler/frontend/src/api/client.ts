import { SERVICE_BASE } from '../basePath';
import type { ApiErrorBody } from '../types/api';

export { SERVICE_BASE };

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorText = await response.text();
    let message = errorText || `HTTP ${response.status}`;
    try {
      const errorJson = JSON.parse(errorText) as ApiErrorBody;
      if (errorJson.message) message = errorJson.message;
    } catch {
      // use raw text
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}
