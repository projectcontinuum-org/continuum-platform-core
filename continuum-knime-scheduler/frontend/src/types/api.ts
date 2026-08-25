// API types matching the continuum-knime-scheduler backend models

export interface KnimeWorkflowResponse {
  workflowId: string;
  fileName: string;
  sizeBytes: number;
  contentType: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface KnimeWorkflowExecutionResponse {
  executionId: string;
  workflowId: string;
  fileName: string;
  sizeBytes: number;
  contentType: string | null;
  status: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateKnimeWorkflowScheduleRequest {
  name: string;
  cronExpression: string;
  timeZone?: string;
  knimeWorkflowId: string;
  resetWorkflow: boolean;
  timeoutSeconds: number;
}

export interface KnimeWorkflowScheduleResponse {
  scheduleId: string;
  name: string;
  ownedBy: string;
  cronExpression: string;
  timeZone: string | null;
  paused: boolean;
  nextRunTimes: string[];
  createdAt: string;
  updatedAt: string;
  knimeWorkflowId: string;
  resetWorkflow: boolean;
  timeoutSeconds: number;
}

export interface ApiErrorBody {
  status: number;
  message: string | null;
}
