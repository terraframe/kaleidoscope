import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { JobStartResponse } from '../models/chat.model';
import { ReportSelection } from '../models/report.model';
import { apiBase } from './url.util';
import { pollJob } from './job-polling.util';

/**
 * Client for net.geoprism.geoai.explorer.web.controller.ReportController.
 *
 * Report generation invokes a Bedrock AgentCore harness that can take several
 * minutes -- well past the ALB's 60 second idle timeout -- so the server runs
 * it as a background job:
 *
 * 1. POST /api/report/generate/start returns a job id immediately.
 * 2. GET /api/report/generate/status/{jobId} is polled (see
 *    job-polling.util.ts) until the job SUCCEEDS or FAILS. The PDF is left
 *    out of the status payload.
 * 3. GET /api/report/generate/result/{jobId} downloads the finished PDF.
 */
@Injectable({
  providedIn: 'root',
})
export class ReportService {
  constructor(private http: HttpClient) {}

  /**
   * Generates a report from `selection` and resolves with the finished PDF.
   *
   * @param onJobStarted Invoked with the job id as soon as it is known, so the
   * caller can persist it and pick the job back up via {@link resumeGenerate}
   * if the page is reloaded before the job settles.
   */
  generate(selection: ReportSelection, onJobStarted?: (jobId: string) => void): Promise<Blob> {
    return firstValueFrom(
      this.http.post<JobStartResponse>(apiBase() + 'api/report/generate/start', selection)
    ).then(({ jobId }) => {
      onJobStarted?.(jobId);
      return this.resumeGenerate(jobId);
    });
  }

  /**
   * Waits for a generate job previously started by {@link generate} to finish,
   * then downloads its PDF.
   */
  resumeGenerate(jobId: string): Promise<Blob> {
    const id = encodeURIComponent(jobId);

    return pollJob<void>(this.http, apiBase() + 'api/report/generate/status/' + id).then(() =>
      this.getResult(jobId)
    );
  }

  /** Downloads the PDF of a generate job that has SUCCEEDED. */
  getResult(jobId: string): Promise<Blob> {
    return firstValueFrom(
      this.http.get(apiBase() + 'api/report/generate/result/' + encodeURIComponent(jobId), {
        responseType: 'blob',
      })
    );
  }
}
