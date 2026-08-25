import { SERVICE_BASE } from '../basePath';
import type { ApiErrorBody } from '../types/api';

export { SERVICE_BASE };

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

export function extractFileName(response: Response, fallback: string): string {
  const disposition = response.headers.get('content-disposition');
  const match = disposition?.match(/filename="([^"]+)"/);
  return match ? match[1] : fallback;
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
