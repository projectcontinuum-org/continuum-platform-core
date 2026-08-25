import type { KnimeWorkflowExecutionResponse, PageResponse } from '../types/api';
import { SERVICE_BASE, handleResponse, extractFileName } from './client';

const executionsBase = (workflowId: string) => `${SERVICE_BASE}/api/v1/knime-workflows/${workflowId}/executions`;

export const knimeWorkflowExecutionsApi = {
  async list(workflowId: string, page = 0, size = 20): Promise<PageResponse<KnimeWorkflowExecutionResponse>> {
    const response = await fetch(`${executionsBase(workflowId)}?page=${page}&size=${size}&sort=createdAt,desc`, {
      method: 'GET',
    });
    return handleResponse<PageResponse<KnimeWorkflowExecutionResponse>>(response);
  },

  async download(workflowId: string, executionId: string, fallbackFileName: string): Promise<void> {
    const response = await fetch(`${executionsBase(workflowId)}/${executionId}/content`, {
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
};
