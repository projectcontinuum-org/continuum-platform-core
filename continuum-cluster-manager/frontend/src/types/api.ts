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
  serviceEndpoint: string | null;
  createdAt: string;
  updatedAt: string;
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

