import type { CreateKnimeWorkflowScheduleRequest, KnimeWorkflowScheduleResponse } from '../types/api';
import { SERVICE_BASE, handleResponse } from './client';

const API_BASE = `${SERVICE_BASE}/api/v1/knime-workflow-schedules`;

// Note: x-continuum-user-id is injected by the boundary service upstream.
// Do not set it here.

const jsonHeaders: HeadersInit = { 'Content-Type': 'application/json' };

export const knimeSchedulesApi = {
  async create(request: CreateKnimeWorkflowScheduleRequest): Promise<KnimeWorkflowScheduleResponse> {
    const response = await fetch(API_BASE, {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify(request),
    });
    return handleResponse<KnimeWorkflowScheduleResponse>(response);
  },

  async list(): Promise<KnimeWorkflowScheduleResponse[]> {
    const response = await fetch(API_BASE, {
      method: 'GET',
    });
    return handleResponse<KnimeWorkflowScheduleResponse[]>(response);
  },

  async get(scheduleId: string): Promise<KnimeWorkflowScheduleResponse> {
    const response = await fetch(`${API_BASE}/${scheduleId}`, {
      method: 'GET',
    });
    return handleResponse<KnimeWorkflowScheduleResponse>(response);
  },

  async pause(scheduleId: string, note?: string): Promise<void> {
    const query = note ? `?note=${encodeURIComponent(note)}` : '';
    const response = await fetch(`${API_BASE}/${scheduleId}/pause${query}`, {
      method: 'POST',
    });
    return handleResponse<void>(response);
  },

  async unpause(scheduleId: string, note?: string): Promise<void> {
    const query = note ? `?note=${encodeURIComponent(note)}` : '';
    const response = await fetch(`${API_BASE}/${scheduleId}/unpause${query}`, {
      method: 'POST',
    });
    return handleResponse<void>(response);
  },

  async trigger(scheduleId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${scheduleId}/trigger`, {
      method: 'POST',
    });
    return handleResponse<void>(response);
  },

  async remove(scheduleId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/${scheduleId}`, {
      method: 'DELETE',
    });
    return handleResponse<void>(response);
  },
};
