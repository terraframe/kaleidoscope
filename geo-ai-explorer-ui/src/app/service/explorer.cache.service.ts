import { Injectable } from '@angular/core';

import { LocationPage } from '../models/chat.model';
import { GeoObject } from '../models/geoobject.model';
import { GprGraph } from '../graph-explorer/graph-explorer.component';
import { ExplorerService } from './explorer.service';
import { ExplorerSessionStateService } from './explorer-session-state.service';

export interface CachedPageResult {
  pageCacheId: string;
  page: LocationPage;
}

/**
 * Caching delegate for {@link ExplorerService}. Exposes the same API, forwarding
 * every call to the wrapped ExplorerService, but serves {@link getPage} and {@link fullTextLookup} results
 * from the ExplorerSessionStateService page cache when available and caches
 * fresh results on the way back.
 */
@Injectable({
  providedIn: 'root',
})
export class ExplorerCacheService {
  constructor(
    private delegate: ExplorerService,
    private explorerSessionState: ExplorerSessionStateService
  ) {}

  neighborQuery(uri: string, excludedTypes: string[] | undefined = undefined): Promise<GprGraph> {
    return this.delegate.neighborQuery(uri, excludedTypes);
  }

  fullTextLookup(query: string): Promise<CachedPageResult> {
    const pageCacheId = this.explorerSessionState.getOrCreateFullTextLookupId(query);

    const cachedPage = this.explorerSessionState.getPages(pageCacheId)?.[0];

    if (cachedPage) {
      return Promise.resolve({ pageCacheId, page: cachedPage });
    }

    return this.delegate.fullTextLookup(query).then((page) => {
      this.explorerSessionState.cachePages([page], pageCacheId);
      return { pageCacheId, page };
    });
  }

  getAttributes(uri: string, includeGeometry: boolean = false, hasPrefix: boolean = true): Promise<GeoObject> {
    return this.delegate.getAttributes(uri, includeGeometry, hasPrefix);
  }

  getPage(
    statement: string,
    type: string | null,
    offset: number,
    limit: number,
    excludedTypes: string[] = [],
    sortField?: string | null,
    sortDirection?: 'asc' | 'desc' | null
  ): Promise<CachedPageResult> {
    const pageCacheId = this.explorerSessionState.getOrCreatePageRequestId(
      statement,
      type,
      offset,
      limit,
      excludedTypes,
      sortField,
      sortDirection
    );

    const cachedPage = this.explorerSessionState.getPages(pageCacheId)?.[0];

    if (cachedPage) {
      return Promise.resolve({ pageCacheId, page: cachedPage });
    }

    return this.delegate
      .getPage(statement, type, offset, limit, excludedTypes, sortField, sortDirection)
      .then((page) => {
        this.explorerSessionState.cachePages([page], pageCacheId);
        return { pageCacheId, page };
      });
  }

  exportPage(
    statement: string,
    type: string | null,
    excludedTypes: string[] = [],
    sortField?: string | null,
    sortDirection?: 'asc' | 'desc' | null
  ): Promise<Blob> {
    return this.delegate.exportPage(statement, type, excludedTypes, sortField, sortDirection);
  }
}
