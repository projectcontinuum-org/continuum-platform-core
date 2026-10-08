// API types matching the backend models

export interface ResourceSpec {
  cpuRequest: string;
  cpuLimit: string;
  memoryRequest: string;
  memoryLimit: string;
  storageSize: string;
  storageClassName: string | null;
}

export interface WorkbenchCreateRequest {
  instanceName: string;
  resources?: Partial<ResourceSpec>;
  image?: string;
  variant?: string;
}

export interface WorkbenchUpdateRequest {
  resources?: Partial<ResourceSpec>;
  image?: string;
}

export interface WorkbenchResponse {
  instanceId: string;
  instanceName: string;
  namespace: string;
  userId: string;
  status: WorkbenchStatus;
  image: string;
  resources: ResourceSpec;
  overlayVariant: string | null;
  serviceEndpoint: string | null;
  ingressUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkbenchLivenessResponse {
  ready: boolean;
  status: WorkbenchStatus;
}

export type WorkbenchStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'FAILED'
  | 'SUSPENDED'
  | 'UNKNOWN'
  | 'TERMINATING'
  | 'DELETED';

// Default values for new workbench
export const DEFAULT_RESOURCES: ResourceSpec = {
  cpuRequest: '500m',
  cpuLimit: '2',
  memoryRequest: '512Mi',
  memoryLimit: '1Gi',
  storageSize: '5Gi',
  storageClassName: null,
};

export const WORKBENCH_IMAGE_REPOSITORY = 'projectcontinuum/continuum-workbench';
export const DEFAULT_IMAGE_TAG = 'latest';
export const DEFAULT_IMAGE = `${WORKBENCH_IMAGE_REPOSITORY}:${DEFAULT_IMAGE_TAG}`;

export interface DockerHubTag {
  name: string;
  lastUpdated: string | null;
  fullSize: number | null;
}

// ── Feature Workers ──────────────────────────────────────────────────

export interface FeatureWorkerResourceSpec {
  cpuRequest: string;
  cpuLimit: string;
  memoryRequest: string;
  memoryLimit: string;
  storageSize: string | null;
  storageClassName: string | null;
}

export interface AutoscalingSpec {
  enabled: boolean;
  minReplicas: number | null;
  maxReplicas: number | null;
  targetCPUUtilizationPercentage: number | null;
}

export interface FeatureWorkerCreateRequest {
  workerName: string;
  image: string;
  taskQueue: string;
  resources?: Partial<FeatureWorkerResourceSpec>;
  replicas?: number;
  autoscaling?: AutoscalingSpec;
  pvcEnabled?: boolean;
  envVars?: Record<string, string>;
  variant?: string;
}

export interface FeatureWorkerUpdateRequest {
  image?: string;
  taskQueue: string;
  resources?: Partial<FeatureWorkerResourceSpec>;
  replicas?: number;
  autoscaling?: AutoscalingSpec;
  pvcEnabled?: boolean;
  envVars?: Record<string, string>;
}

export interface FeatureWorkerResponse {
  workerId: string;
  workerName: string;
  namespace: string;
  createdBy: string;
  status: FeatureWorkerStatus;
  image: string;
  taskQueue: string;
  replicas: number;
  autoscaling: AutoscalingSpec;
  resources: FeatureWorkerResourceSpec;
  pvcEnabled: boolean;
  envVars: Record<string, string>;
  overlayVariant: string | null;
  createdAt: string;
  updatedAt: string;
}

export type FeatureWorkerStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'FAILED'
  | 'UNKNOWN'
  | 'TERMINATING'
  | 'DELETED';

export const DEFAULT_FEATURE_WORKER_RESOURCES: FeatureWorkerResourceSpec = {
  cpuRequest: '500m',
  cpuLimit: '2',
  memoryRequest: '512Mi',
  memoryLimit: '1Gi',
  storageSize: '5Gi',
  storageClassName: null,
};

export const TASK_QUEUE_PATTERN = /^CONTINUUM-FEATURE-[A-Z0-9-]+-TASK-QUEUE$/;

