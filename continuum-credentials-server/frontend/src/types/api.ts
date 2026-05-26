// API types matching the backend models

export interface CredentialCreateRequest {
  name: string;
  type: string;
  typeVersion: string;
  data: Record<string, string>;
  description?: string;
}

export interface CredentialUpdateRequest {
  name?: string;
  type?: string;
  typeVersion?: string;
  data?: Record<string, string>;
  description?: string;
}

export interface CredentialResponse {
  userId: string;
  name: string;
  type: string;
  typeVersion: string;
  data: Record<string, string>;
  description: string | null;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
  lastAccessedAt: string | null;
}

export interface CredentialTypeResponse {
  type: string;
  schema: Record<string, unknown>;
  uiSchema: Record<string, unknown>;
  version: string;
  createdAt: string;
  updatedAt: string;
}
