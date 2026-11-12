import { Injectable } from '@angular/core';
import { GeoObject } from '../models/geoobject.model';
import { GprGraph } from '../graph-explorer/graph-explorer.component';
import { HttpClient, HttpParams } from '@angular/common/http';
import { apiBase } from './url.util';
import { firstValueFrom } from 'rxjs';
import { JobStartResponse, LocationPage } from '../models/chat.model';
import { pollJob } from './job-polling.util';
import { MockUtil } from '../mock-util';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ExplorerService {
  constructor(private http: HttpClient) {}

  neighborQuery(uri: string, excludedTypes: string[] | undefined = undefined): Promise<GprGraph> {
    let sExcludedTypes = null;
    if (excludedTypes != undefined) {
      sExcludedTypes = excludedTypes.join(',');
    }

    /*
     * This neighbor lookup runs a SPARQL graph query on the server that can
     * be slow for highly-connected nodes -- long enough to exceed the ALB's
     * 60 second idle timeout and produce a 504 Gateway Timeout. As with
     * chat/prompt and chat/get-locations, the server now kicks the query
     * off in the background and returns a job id immediately; we poll its
     * status (see job-polling.util.ts) until it completes. The returned
     * Promise still resolves/rejects exactly as before, so callers (and
     * their loading spinners) need no changes.
     */
    return firstValueFrom(
      this.http.post<JobStartResponse>(apiBase() + 'api/neighbors/start', { uri: uri, excludedTypes: sExcludedTypes })
    ).then(({ jobId }) => pollJob<GprGraph>(this.http, apiBase() + 'api/neighbors/status/' + jobId));
  }

  fullTextLookup(query: string): Promise<LocationPage> {
    return firstValueFrom(this.http.post<LocationPage>(apiBase() + 'api/full-text-lookup', { query: query }));
  }

  getAttributes(uri: string, includeGeometry: boolean = false, hasPrefix: boolean = true): Promise<GeoObject> {
    // Uncomment below to make a real HTTP request
    let params = new HttpParams();
    params = params.append('uri', uri);
    params = params.append('includeGeometry', includeGeometry);
    params = params.append('hasPrefix', hasPrefix);

    return firstValueFrom(this.http.get<GeoObject>(apiBase() + 'api/get-attributes', { params }));
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
    if (environment.mockRequests) {
      return new Promise<LocationPage>((resolve) => {
        setTimeout(() => {
          resolve(MockUtil.locations);
        }, 500); // Simulating network delay
      });
    } else {
      const params = {
        statement,
        type,
        limit,
        offset,
        excludedTypes,
        sortField,
        sortDirection,
      };

      return firstValueFrom(this.http.post<LocationPage>(apiBase() + 'api/get-page', params));
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
      this.http.post(apiBase() + 'api/export-page', params, {
        responseType: 'blob',
      })
    );
  }
}
