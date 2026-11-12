import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';

import { ChatMessage, LocationPage } from '../models/chat.model';

export type CachedExplorerPagesSource = 'chat-mapit' | 'full-text-disambiguation' | 'page-query' | 'unknown';

export interface CachedExplorerPages {
  id: string;
  createdAt: number;
  updatedAt: number;
  accessedAt: number;
  savedAt?: number;
  pages: LocationPage[];
  source: CachedExplorerPagesSource;
  title: string;
  saved: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class ExplorerSessionStateService {
  private readonly prefix = 'explorer.pages.';
  private readonly savedPrefix = 'explorer.savedPages.';
  private readonly maxSessionCaches = 25;
  private readonly maxQuotaWriteAttempts = 12;

  private readonly savedQueriesSubject = new BehaviorSubject<CachedExplorerPages[]>(this.listSavedPages());

  readonly savedQueries$ = this.savedQueriesSubject.asObservable();

  cachePages(
    pages: LocationPage[],
    source: CachedExplorerPagesSource = 'unknown',
    id: string = uuidv4(),
    title?: string
  ): string {
    console.log('cachePages: ' + id, pages);

    if (pages.length > 1) {
      try {
        throw new Error('Failure');
      } catch (err) {
        const stackTrace = new Error().stack;
        console.log('Custom captured stack:\n', stackTrace);
      }
    }

    if (!this.hasResults(pages)) {
      this.deleteCachedPages(id);
      return id;
    }

    const existing = this.getCache(id);
    const saved = existing?.saved ?? this.hasSavedCache(id);

    const cache: CachedExplorerPages = {
      id,
      createdAt: existing?.createdAt ?? Date.now(),
      updatedAt: Date.now(),
      accessedAt: Date.now(),
      savedAt: existing?.savedAt,
      pages,
      source: source === 'unknown' ? (existing?.source ?? source) : source,
      title: title ?? existing?.title ?? this.buildTitle(pages, source),
      saved,
    };

    try {
      this.pruneSessionCaches();

      if (saved) {
        sessionStorage.removeItem(this.storageKey(id));

        if (!this.setLocalItemWithSessionEviction(this.savedStorageKey(id), JSON.stringify(cache), id)) {
          throw new Error(`Unable to update saved explorer pages [${id}] after session cache eviction.`);
        }
      } else {
        if (!this.setSessionItemWithEviction(this.storageKey(id), JSON.stringify(cache), id)) {
          throw new Error(`Unable to cache explorer pages [${id}] after session cache eviction.`);
        }
      }

      this.pruneSessionCaches();
      this.refreshSavedQueries();
    } catch (error) {
      console.warn('Failed to cache explorer pages in sessionStorage', error);
    }

    return id;
  }

  hasResults(pages: LocationPage[] | null | undefined): boolean {
    return (pages ?? []).some((page) => (page.count ?? 0) > 0 || (page.locations?.length ?? 0) > 0);
  }

  getPages(id: string | null | undefined): LocationPage[] | null {
    console.log('Getting page: ' + id);

    if (!id) {
      return null;
    }

    try {
      const cache = this.getCache(id);

      console.log('Cache: ', cache);

      if (!cache) {
        return null;
      }

      cache.accessedAt = Date.now();

      if (cache.saved || this.hasSavedCache(id)) {
        sessionStorage.removeItem(this.storageKey(id));
        this.setLocalItemWithSessionEviction(this.savedStorageKey(id), JSON.stringify({ ...cache, saved: true }), id);
      } else {
        this.setSessionItemWithEviction(this.storageKey(id), JSON.stringify(cache), id);
      }

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

  getOrCreateLocationsRequestId(messages: ChatMessage[], offset: number, limit: number): string {
    const key = JSON.stringify({
      messages: messages.map((message) => ({
        sender: message.sender,
        text: message.text,
        purpose: message.purpose,
      })),
      offset,
      limit,
    });

    return ['locations-query', this.hash(key)].join('.');
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
        saved: true,
      });
    });

    return Array.from(caches.values()).sort((a, b) => b.updatedAt - a.updatedAt);
  }

  listSavedPages(): CachedExplorerPages[] {
    return this.collectCaches(localStorage, this.savedPrefix)
      .map((cache) => ({ ...cache, saved: true }))
      .sort((a, b) => b.updatedAt - a.updatedAt);
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
      if (touched.saved || this.hasSavedCache(id)) {
        sessionStorage.removeItem(this.storageKey(id));
        this.setLocalItemWithSessionEviction(this.savedStorageKey(id), JSON.stringify({ ...touched, saved: true }), id);
      } else {
        this.setSessionItemWithEviction(this.storageKey(id), JSON.stringify(touched), id);
      }
    } catch (error) {
      console.warn('Failed to update cached explorer pages access time', error);
    }

    return touched;
  }

  getSavedQueryIndex(id: string | null | undefined): number | null {
    if (!id) {
      return null;
    }

    const index = this.listSavedPages().findIndex((cache) => cache.id === id);

    return index === -1 ? null : index + 1;
  }

  saveCachedPages(id: string, title?: string): boolean {
    const cache = this.getCache(id);

    if (!cache) {
      return false;
    }

    const trimmedTitle = title?.trim();
    const savedCache: CachedExplorerPages = {
      ...cache,
      saved: true,
      savedAt: cache.savedAt ?? Date.now(),
      updatedAt: Date.now(),
      title: trimmedTitle && trimmedTitle.length > 0 ? trimmedTitle : cache.title,
    };

    const serialized = JSON.stringify(savedCache);

    try {
      this.pruneSessionCaches();

      sessionStorage.removeItem(this.storageKey(id));

      if (!this.setLocalItemWithSessionEviction(this.savedStorageKey(id), serialized, id)) {
        throw new Error(`Unable to save explorer pages [${id}] after session cache eviction.`);
      }

      this.refreshSavedQueries();
      return true;
    } catch (error) {
      console.warn('Failed to save explorer pages cache', error);
      return false;
    }
  }

  renameSavedPages(id: string, title: string): void {
    const trimmedTitle = title.trim();

    if (!trimmedTitle) {
      return;
    }

    const cache = this.getCache(id);

    if (!cache) {
      return;
    }

    const renamedCache: CachedExplorerPages = {
      ...cache,
      title: trimmedTitle,
      saved: true,
      savedAt: cache.savedAt ?? Date.now(),
      updatedAt: Date.now(),
    };

    try {
      const serialized = JSON.stringify(renamedCache);

      sessionStorage.removeItem(this.storageKey(id));

      if (!this.setLocalItemWithSessionEviction(this.savedStorageKey(id), serialized, id)) {
        throw new Error(`Unable to rename explorer pages [${id}] after session cache eviction.`);
      }

      this.refreshSavedQueries();
    } catch (error) {
      console.warn('Failed to rename saved explorer pages cache', error);
    }
  }

  unsaveCachedPages(id: string): void {
    const cache = this.getCache(id);

    if (!cache) {
      return;
    }

    const unsavedCache: CachedExplorerPages = {
      ...cache,
      saved: false,
      savedAt: undefined,
      updatedAt: Date.now(),
    };

    try {
      localStorage.removeItem(this.savedStorageKey(id));

      if (!this.setSessionItemWithEviction(this.storageKey(id), JSON.stringify(unsavedCache), id)) {
        throw new Error(`Unable to unsave explorer pages [${id}] in session cache after eviction.`);
      }

      this.refreshSavedQueries();
    } catch (error) {
      console.warn('Failed to unsave explorer pages cache', error);
    }
  }

  deleteCachedPages(id: string): void {
    try {
      sessionStorage.removeItem(this.storageKey(id));
      localStorage.removeItem(this.savedStorageKey(id));
      this.refreshSavedQueries();
    } catch (error) {
      console.warn('Failed to delete explorer pages cache', error);
    }
  }

  private getCache(id: string | null | undefined): CachedExplorerPages | null {
    console.log('Getting cache: ' + id);

    if (!id) {
      return null;
    }

    const sessionCache = this.readCache(sessionStorage, this.storageKey(id));

    if (sessionCache) {
      return {
        ...sessionCache,
        saved: sessionCache.saved || this.hasSavedCache(id),
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

  private hasSavedCache(id: string): boolean {
    return localStorage.getItem(this.savedStorageKey(id)) != null;
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
        savedAt: cache.savedAt ?? (cache.saved ? (cache.updatedAt ?? cache.createdAt ?? Date.now()) : undefined),
        title: cache.title ?? this.buildTitle(cache.pages, cache.source ?? 'unknown'),
        saved: cache.saved ?? false,
      };
    } catch (error) {
      console.warn('Failed to parse cached explorer pages', error);
      return null;
    }
  }

  private refreshSavedQueries(): void {
    this.savedQueriesSubject.next(this.listSavedPages());
  }

  private pruneSessionCaches(): void {
    const sessionCaches = this.collectCaches(sessionStorage, this.prefix);
    const disposable = sessionCaches
      .filter((cache) => !cache.saved && !this.hasSavedCache(cache.id))
      .sort((a, b) => a.accessedAt - b.accessedAt);

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
        console.log('Setting session storage - ' + key, value);

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

  private setLocalItemWithSessionEviction(key: string, value: string, protectedId?: string): boolean {
    for (let attempt = 0; attempt < this.maxQuotaWriteAttempts; attempt++) {
      try {
        localStorage.setItem(key, value);
        return true;
      } catch (error) {
        if (!this.isQuotaExceededError(error) || !this.evictSessionCacheEntry(protectedId)) {
          console.warn('Failed to write saved explorer pages cache to localStorage', error);
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

    const unsaved = sessionCaches.find((cache) => !cache.saved && !this.hasSavedCache(cache.id));
    const evictable = unsaved ?? sessionCaches.find((cache) => cache.saved || this.hasSavedCache(cache.id));

    if (!evictable) {
      return false;
    }

    sessionStorage.removeItem(this.storageKey(evictable.id));
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

  private buildTitle(pages: LocationPage[], source: CachedExplorerPagesSource): string {
    const total = pages.reduce((sum, page) => sum + (page.count ?? page.locations?.length ?? 0), 0);
    const typeLabels = pages.map((page) => this.typeLabel(page.type)).filter((label) => label.length > 0);
    const uniqueTypeLabels = Array.from(new Set(typeLabels));
    const label = uniqueTypeLabels.length > 0 ? uniqueTypeLabels.slice(0, 2).join(', ') : this.sourceLabel(source);

    return total > 0 ? `${label} (${total})` : label;
  }

  private typeLabel(type: string | null | undefined): string {
    if (!type?.trim()) {
      return '';
    }

    const hashIndex = type.lastIndexOf('#');
    const slashIndex = type.lastIndexOf('/');
    const index = Math.max(hashIndex, slashIndex);

    return index === -1 ? type : type.substring(index + 1);
  }

  private sourceLabel(source: CachedExplorerPagesSource): string {
    if (source === 'chat-mapit') return 'Mapped chat query';
    if (source === 'full-text-disambiguation') return 'Disambiguation query';
    if (source === 'page-query') return 'Page query';

    return 'Cached query';
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
