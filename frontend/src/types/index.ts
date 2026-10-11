export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface ApiErrorResponse {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  errors?: string[];
  message?: string;
}

export interface User {
  id: number;
  username: string;
  email: string;
  totalPoints: number;
  createdAt: string;
}

export interface HealthInfo {
  status: string;
  application: string;
  phase: string;
}

export type Mood =
  | 'STRESSED'
  | 'TIRED'
  | 'ANXIOUS'
  | 'BORED'
  | 'ENERGETIC';

export type EnvironmentType =
  | 'CAMPUS'
  | 'PARK'
  | 'NEIGHBORHOOD'
  | 'BALCONY_YARD'
  | 'NATURE_TRAIL';

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export type Participants = 'SOLO' | 'FRIENDS' | 'FAMILY';

export type MissionStatus =
  | 'CREATED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED';

export interface MissionGenerateRequest {
  availableMinutes: number;
  mood: Mood;
  goal: string;
  environmentType: EnvironmentType;
  difficulty: Difficulty;
  participants: Participants;
  userId?: number;
}

export interface TaskCompletionRequest {
  actualDurationMinutes?: number;
}

export interface MissionTask {
  id: number;
  description: string;
  estimatedMinutes: number;
  taskOrder: number;
  completed: boolean;
}

export interface Mission {
  id: number;
  userId: number | null;
  title: string;
  description: string;
  availableMinutes: number;
  mood: Mood;
  goal: string;
  environmentType: EnvironmentType;
  difficulty: Difficulty;
  participants: Participants;
  status: MissionStatus;
  pointsAwarded: number;
  bonusTask: string | null;
  createdAt: string;
  completedAt: string | null;
  tasks: MissionTask[];
}
