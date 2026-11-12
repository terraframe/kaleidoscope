import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';

import { ChatMessage, JobStartResponse, LocationPage, ServerChatResponse } from '../models/chat.model';
import { MockUtil } from '../mock-util';
import { environment } from '../../environments/environment';
import { apiBase } from './url.util';
import { ExplorerSessionStateService } from './explorer-session-state.service';
import { pollJob } from './job-polling.util';

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  /*
   * chat/prompt and chat/get-locations both invoke a Bedrock AgentCore
   * harness on the server that can take up to several minutes to respond
   * -- well beyond the ALB's 60 second idle timeout, which was causing
   * 504 Gateway Timeout errors. Instead of waiting on a single long HTTP
   * request, the server now kicks the work off in the background and
   * returns a job id immediately; we poll its status here (see
   * job-polling.util.ts) until it completes. This is all internal to this
   * service -- callers still get back a Promise that resolves/rejects
   * exactly as before, so the busy spinner and every other bit of UI/UX
   * driven off that Promise needs no changes.
   */

  constructor(
    private http: HttpClient,
    private explorerSessionState: ExplorerSessionStateService
  ) {}

  /**
   * @param onJobStarted Invoked synchronously (before the job completes)
   * with the job's status URL, as soon as it's known. AichatComponent uses
   * this to stamp the status URL onto its "AI is thinking" placeholder
   * message and persist it, so that if the page is closed or reloaded
   * before the job settles, it can resume polling (or fail the message
   * cleanly) on the next boot via {@link resumePrompt}.
   */
  sendMessage(
    sessionId: string,
    message: ChatMessage,
    onJobStarted?: (statusUrl: string) => void
  ): Promise<ChatMessage> {
    if (environment.mockRequests) {
      return new Promise<ChatMessage>((resolve) => {
        setTimeout(() => {
          resolve(MockUtil.message);
        }, 500); // Simulating network delay
      });
    } else {
      // Sent in the body so long prompts aren't limited by URL length
      const params = { sessionId: sessionId, prompt: message.text };

      return firstValueFrom(this.http.post<JobStartResponse>(apiBase() + 'api/chat/prompt/start', params))
        .then(({ jobId }) => {
          const statusUrl = apiBase() + 'api/chat/prompt/status/' + jobId;
          onJobStarted?.(statusUrl);
          return pollJob<ServerChatResponse>(this.http, statusUrl);
        })
        .then((response) => this.toChatMessage(response));
    }
  }

  /**
   * Resumes polling a chat/prompt job from a status URL that was previously
   * handed to `onJobStarted` in {@link sendMessage}. Used on app boot to
   * recover a message that was left mid-flight because the page was closed
   * or reloaded before the original request settled -- see
   * AichatComponent.reconcilePendingJobs. Resolves/rejects exactly like
   * sendMessage's returned Promise (including a fast rejection if the
   * server no longer recognizes the job).
   */
  resumePrompt(statusUrl: string): Promise<ChatMessage> {
    return pollJob<ServerChatResponse>(this.http, statusUrl).then((response) => this.toChatMessage(response));
  }

  private toChatMessage(response: ServerChatResponse): ChatMessage {
    return {
      id: uuidv4(),
      sender: 'system',
      text: response.content,
      mappable: response.mappable,
      ambiguous: response.ambiguous,
      purpose: 'standard',
      location: response.location,
      reasoning: response.reasoning,
      statements: response.statements,
      mapStatement: response.mapStatement ?? null,
    };
  }

  getPage(
    statement: string,
    type: string | null,
    offset: number,
    limit: number,
    excludedTypes: string[] = [],
    sortField?: string | null,
    sortDirection?: 'asc' | 'desc' | null
  ): Promise<LocationPage> {
    const cacheId = this.explorerSessionState.getOrCreatePageRequestId(
      statement,
      type,
      offset,
      limit,
      excludedTypes,
      sortField,
      sortDirection
    );

    const cachedPages = this.explorerSessionState.getPages(cacheId);
    const cachedPage = cachedPages?.[0];

    if (cachedPage) {
      return Promise.resolve(cachedPage);
    }

    if (environment.mockRequests) {
      return new Promise<LocationPage>((resolve) => {
        setTimeout(() => {
          resolve(MockUtil.locations);
        }, 500); // Simulating network delay
      }).then((page) => {
        this.explorerSessionState.cachePages([page], 'page-query', cacheId);
        return page;
      });
    } else {
      // // // Uncomment below to make a real HTTP request
      const params = {
        statement,
        type,
        limit,
        offset,
        excludedTypes,
        sortField,
        sortDirection,
      };

      return firstValueFrom(this.http.post<LocationPage>(apiBase() + 'api/chat/get-page', params)).then((page) => {
        this.explorerSessionState.cachePages([page], 'page-query', cacheId);
        return page;
      });
    }
  }

  exportPage(
    statement: string,
    type: string | null,
    excludedTypes: string[] = [],
    sortField?: string | null,
    sortDirection?: 'asc' | 'desc' | null
  ): Promise<Blob> {
    const params = {
      statement,
      type,
      limit: 0,
      offset: 0,
      excludedTypes,
      sortField,
      sortDirection,
    };

    return firstValueFrom(
      this.http.post(apiBase() + 'api/chat/export-page', params, {
        responseType: 'blob',
      })
    );
  }
}
