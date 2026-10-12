import type { KnimeWorkflowResponse, PageResponse } from '../types/api';
import { SERVICE_BASE, handleResponse, extractFileName } from './client';

const API_BASE = `${SERVICE_BASE}/api/v1/knime-workflows`;

// Note: x-continuum-user-id is injected by the boundary service upstream.
// Do not set it here.

export const knimeWorkflowsApi = {
  async upload(file: File): Promise<KnimeWorkflowResponse> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await fetch(API_BASE, {
      method: 'POST',
      body: formData,
    });
    return handleResponse<KnimeWorkflowResponse>(response);
  },

  async list(page = 0, size = 20): Promise<PageResponse<KnimeWorkflowResponse>> {
    const response = await fetch(`${API_BASE}?page=${page}&size=${size}`, {
      method: 'GET',
    });
    return handleResponse<PageResponse<KnimeWorkflowResponse>>(response);
  },

  async get(workflowId: string): Promise<KnimeWorkflowResponse> {
    const response = await fetch(`${API_BASE}/${workflowId}`, {
      method: 'GET',
    });
    return handleResponse<KnimeWorkflowResponse>(response);
  },

  async download(workflowId: string, fallbackFileName: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${workflowId}/content`, {
      method: 'GET',
    });
    if (!response.ok) {
      await handleResponse(response);
      return;
    }
    const fileName = extractFileName(response, fallbackFileName);
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  },

  async replace(workflowId: string, file: File): Promise<KnimeWorkflowResponse> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await fetch(`${API_BASE}/${workflowId}`, {
      method: 'PUT',
      body: formData,
    });
    return handleResponse<KnimeWorkflowResponse>(response);
  },

  async remove(workflowId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${workflowId}`, {
      method: 'DELETE',
    });
    return handleResponse<void>(response);
  },
};
