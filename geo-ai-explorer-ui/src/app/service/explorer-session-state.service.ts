import { Injectable } from '@angular/core';
import { v4 as uuidv4 } from 'uuid';

import { LocationPage } from '../models/chat.model';

export interface CachedExplorerPages {
  id: string;
  createdAt: number;
  updatedAt: number;
  accessedAt: number;
  pages: LocationPage[];
}

@Injectable({
  providedIn: 'root',
})
export class ExplorerSessionStateService {
  private readonly prefix = 'explorer.pages.';
  private readonly savedPrefix = 'explorer.savedPages.';
  private readonly maxSessionCaches = 25;
  private readonly maxQuotaWriteAttempts = 12;

  cachePages(pages: LocationPage[], id: string = uuidv4()): string {
    const existing = this.getCache(id);

    const cache: CachedExplorerPages = {
      id,
      createdAt: existing?.createdAt ?? Date.now(),
      updatedAt: Date.now(),
      accessedAt: Date.now(),
      pages,
    };

    try {
      this.pruneSessionCaches();

      if (!this.setSessionItemWithEviction(this.storageKey(id), JSON.stringify(cache), id)) {
        throw new Error(`Unable to cache explorer pages [${id}] after session cache eviction.`);
      }

      this.pruneSessionCaches();
    } catch (error) {
      console.warn('Failed to cache explorer pages in sessionStorage', error);
    }

    return id;
  }

  getPages(id: string | null | undefined): LocationPage[] | null {
    if (!id) {
      return null;
    }

    try {
      const cache = this.getCache(id);

      if (!cache) {
        return null;
      }

      cache.accessedAt = Date.now();

      this.setSessionItemWithEviction(this.storageKey(id), JSON.stringify(cache), id);

      return Array.isArray(cache.pages) ? cache.pages : null;
    } catch (error) {
      console.warn('Failed to read cached explorer pages from sessionStorage', error);
      return null;
    }
  }

  getOrCreatePageRequestId(
    statement: string,
    type: string | null,
    offset: number,
    limit: number,
    excludedTypes: string[] = [],
    sortField?: string | null,
    sortDirection?: string | null
  ): string {
    return [
      'page-query',
      this.hash(statement),
      this.hash(type ?? ''),
      this.hash(JSON.stringify([...excludedTypes].sort())),
      this.hash(sortField ?? ''),
      this.hash(sortDirection ?? ''),
      offset,
      limit,
    ].join('.');
  }

  getOrCreateFullTextLookupId(query: string): string {
    return ['full-text', this.hash(query)].join('.');
  }

  listCachedPages(): CachedExplorerPages[] {
    const caches = new Map<string, CachedExplorerPages>();

    this.collectCaches(sessionStorage, this.prefix).forEach((cache) => {
      caches.set(cache.id, cache);
    });

    this.collectCaches(localStorage, this.savedPrefix).forEach((cache) => {
      const existing = caches.get(cache.id);

      caches.set(cache.id, {
        ...cache,
        ...existing,
      });
    });

    return Array.from(caches.values()).sort((a, b) => b.updatedAt - a.updatedAt);
  }

  getCachedPages(id: string): CachedExplorerPages | null {
    const cache = this.getCache(id);

    if (!cache) {
      return null;
    }

    const touched = {
      ...cache,
      accessedAt: Date.now(),
    };

    try {
      this.setSessionItemWithEviction(this.storageKey(id), JSON.stringify(touched), id);
    } catch (error) {
      console.warn('Failed to update cached explorer pages access time', error);
    }

    return touched;
  }

  private getCache(id: string | null | undefined): CachedExplorerPages | null {
    if (!id) {
      return null;
    }

    const sessionCache = this.readCache(sessionStorage, this.storageKey(id));

    if (sessionCache) {
      return {
        ...sessionCache,
      };
    }

    const savedCache = this.readCache(localStorage, this.savedStorageKey(id));

    if (savedCache) {
      const restored = {
        ...savedCache,
        saved: true,
      };

      return restored;
    }

    return null;
  }

  private collectCaches(storage: Storage, prefix: string): CachedExplorerPages[] {
    const caches: CachedExplorerPages[] = [];

    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);

      if (!key?.startsWith(prefix)) {
        continue;
      }

      const cache = this.readCache(storage, key);

      if (cache) {
        caches.push(cache);
      }
    }

    return caches;
  }

  private readCache(storage: Storage, key: string): CachedExplorerPages | null {
    const raw = storage.getItem(key);

    if (!raw) {
      return null;
    }

    try {
      const cache = JSON.parse(raw) as CachedExplorerPages;

      if (!cache?.id || !Array.isArray(cache.pages)) {
        return null;
      }

      return {
        ...cache,
        createdAt: cache.createdAt ?? Date.now(),
        updatedAt: cache.updatedAt ?? cache.createdAt ?? Date.now(),
        accessedAt: cache.accessedAt ?? cache.updatedAt ?? cache.createdAt ?? Date.now(),
      };
    } catch (error) {
      console.warn('Failed to parse cached explorer pages', error);
      return null;
    }
  }

  private pruneSessionCaches(): void {
    const sessionCaches = this.collectCaches(sessionStorage, this.prefix);
    const disposable = sessionCaches.sort((a, b) => a.accessedAt - b.accessedAt);

    while (sessionCaches.length > this.maxSessionCaches && disposable.length > 0) {
      const oldest = disposable.shift();

      if (!oldest) {
        break;
      }

      sessionStorage.removeItem(this.storageKey(oldest.id));
      sessionCaches.splice(
        sessionCaches.findIndex((cache) => cache.id === oldest.id),
        1
      );
    }
  }

  private setSessionItemWithEviction(key: string, value: string, protectedId?: string): boolean {
    for (let attempt = 0; attempt < this.maxQuotaWriteAttempts; attempt++) {
      try {
        sessionStorage.setItem(key, value);
        return true;
      } catch (error) {
        if (!this.isQuotaExceededError(error) || !this.evictSessionCacheEntry(protectedId)) {
          console.warn('Failed to write explorer pages cache to sessionStorage', error);
          return false;
        }
      }
    }

    return false;
  }

  private evictSessionCacheEntry(protectedId?: string): boolean {
    const sessionCaches = this.collectCaches(sessionStorage, this.prefix)
      .filter((cache) => cache.id !== protectedId)
      .sort((a, b) => a.accessedAt - b.accessedAt);

    if (sessionCaches.length === 0) {
      return false;
    }

    sessionStorage.removeItem(this.storageKey(sessionCaches[0].id));
    return true;
  }

  private isQuotaExceededError(error: unknown): boolean {
    return (
      error instanceof DOMException &&
      (error.name === 'QuotaExceededError' || error.name === 'NS_ERROR_DOM_QUOTA_REACHED')
    );
  }

  private storageKey(id: string): string {
    return `${this.prefix}${id}`;
  }

  private savedStorageKey(id: string): string {
    return `${this.savedPrefix}${id}`;
  }

  private hash(value: string): string {
    let hash = 0;

    for (let i = 0; i < value.length; i++) {
      hash = (hash << 5) - hash + value.charCodeAt(i);
      hash |= 0;
    }

    return Math.abs(hash).toString(36);
  }
}
