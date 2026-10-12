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

export interface KnimeWorkflowVariable {
  name: string;
  value: string;
  type: string;
}

export interface KnimeWorkflowCredentialRef {
  knimeCredentialName: string;
  credential: string;
}

export interface CredentialSummary {
  name: string;
  type: string;
}

export interface CreateKnimeWorkflowScheduleRequest {
  name: string;
  cronExpression: string;
  timeZone?: string;
  knimeWorkflowId: string;
  resetWorkflow: boolean;
  timeoutSeconds: number;
  workflowVariables: KnimeWorkflowVariable[];
  workflowCredentials: KnimeWorkflowCredentialRef[];
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
  workflowVariables: KnimeWorkflowVariable[];
  workflowCredentials: KnimeWorkflowCredentialRef[];
}

export interface ApiErrorBody {
  status: number;
  message: string | null;
}
