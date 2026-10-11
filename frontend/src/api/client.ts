import {
  ApiResponse,
  ApiErrorResponse,
  HealthInfo,
  User,
  Mission,
  MissionGenerateRequest,
  TaskCompletionRequest,
} from '../types';

const API_BASE = '/api';

async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<ApiResponse<T>> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  });

  const result: (ApiResponse<T> & ApiErrorResponse) | null = await response
    .json()
    .catch(() => null);

  if (!response.ok) {
    let errorMessage = `Request failed with status ${response.status}`;

    if (result) {
      if (Array.isArray(result.errors) && result.errors.length > 0) {
        errorMessage = result.errors.join('. ');
      } else if (result.detail) {
        errorMessage = result.detail;
      } else if (result.message) {
        errorMessage = result.message;
      } else if (result.title) {
        errorMessage = result.title;
      }
    }

    throw new Error(errorMessage);
  }

  return result as ApiResponse<T>;
}

export function fetchHealth(): Promise<ApiResponse<HealthInfo>> {
  return apiRequest<HealthInfo>('/health');
}

export function fetchUsers(): Promise<ApiResponse<User[]>> {
  return apiRequest<User[]>('/users');
}

export function createUser(
  username: string,
  email: string,
): Promise<ApiResponse<User>> {
  return apiRequest<User>('/users', {
    method: 'POST',
    body: JSON.stringify({ username, email }),
  });
}

export function generateMission(
  request: MissionGenerateRequest,
): Promise<ApiResponse<Mission>> {
  return apiRequest<Mission>('/missions/generate', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function fetchMission(
  missionId: number,
): Promise<ApiResponse<Mission>> {
  return apiRequest<Mission>(`/missions/${missionId}`);
}

export function completeTask(
  missionId: number,
  taskId: number,
  actualDurationMinutes?: number,
): Promise<ApiResponse<Mission>> {
  const payload: TaskCompletionRequest | undefined =
    actualDurationMinutes !== undefined && actualDurationMinutes !== null
      ? { actualDurationMinutes }
      : undefined;

  return apiRequest<Mission>(
    `/missions/${missionId}/tasks/${taskId}/complete`,
    {
      method: 'PATCH',
      ...(payload ? { body: JSON.stringify(payload) } : {}),
    },
  );
}
