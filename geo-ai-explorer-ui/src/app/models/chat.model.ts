import { GeoObject } from './geoobject.model';

export interface ChatMessage {
  id: string;
  sender: 'user' | 'system';
  text: string;
  mappable: boolean;
  ambiguous?: boolean;
  location?: string;
  parsedText?: string;
  loading?: boolean;
  purpose: 'info' | 'standard';
  statements: string[];

  /**
   * SPARQL, written by the chat agent alongside its answer, returning the
   * lowest-level objects the answer is based on (with geometries) so the
   * user can visually audit it on a map. Only present when the agent set
   * mapIt (mappable); absent on messages saved before this field existed.
   */
  mapStatement?: string | null;

  /**
   * Optional chain-of-thought / rationale the model produced alongside its
   * answer. When present, the UI surfaces it via a hover popup instead of
   * showing it inline with the response.
   */
  reasoning?: string;
  /**
   * Set only while `loading` is true, on the placeholder message created
   * for an in-flight chat/prompt job. Points at that job's status endpoint
   * (see job-polling.util.ts) and is persisted to localStorage alongside
   * the rest of the conversation, so that if the page is closed or
   * reloaded before the job finishes, AichatComponent can resume polling
   * it on the next boot -- or, if the server no longer recognizes the job
   * (e.g. it restarted since), mark the message failed instead of leaving
   * it stuck on a permanent busy spinner. Cleared once the job settles.
   */
  pendingStatusUrl?: string;
}

export interface ServerChatResponse {
  content: string;
  sessionId: string;
  mappable: boolean;
  ambiguous: boolean;
  conversationTitle?: string;
  location?: string;
  reasoning?: string;
  statements: string[];
  mapStatement?: string | null;
}

export interface TypeSummary {
  type: string;
  count: number;
}

export interface LocationPage {
  statement: string;
  type: string | null;
  locations: GeoObject[];
  limit: number;
  offset: number;
  count: number;
  sortField?: string | null;
  sortDirection?: 'asc' | 'desc' | null;
  availableTypes?: TypeSummary[];
}

/**
 * Returned immediately by the /api/chat/prompt/start and
 * /api/chat/get-locations/start endpoints. The actual response is fetched
 * by polling JobStatusResponse via the returned jobId.
 */
export interface JobStartResponse {
  jobId: string;
}

/**
 * Polled from /api/chat/prompt/status/{jobId} and
 * /api/chat/get-locations/status/{jobId} until status is SUCCEEDED or
 * FAILED. `result` is only populated once SUCCEEDED.
 */
export interface JobStatusResponse<T> {
  jobId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  result?: T;
  errorMessage?: string;
}

export interface Fact {
  id?: number;
  name: string;
  description: string;
  text: string;
  statements: string[];
  mapStatement?: string | null;
  projectId?: number | null;
  projectName?: string;
}

export interface Project {
  id?: number;
  name: string;
}
