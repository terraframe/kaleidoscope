import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { Fact } from '../models/chat.model';
import { apiBase } from './url.util';

@Injectable({
  providedIn: 'root',
})
export class FactService {
  constructor(private http: HttpClient) {}

  save(fact: Fact): Promise<Fact> {
    return firstValueFrom(this.http.post<Fact>(apiBase() + 'api/facts', fact));
  }

  getByProject(projectId: number): Promise<Fact[]> {
    return firstValueFrom(this.http.get<Fact[]>(apiBase() + 'api/facts/' + projectId));
  }

  delete(id: number): Promise<void> {
    return firstValueFrom(this.http.delete(apiBase() + 'api/facts/' + id, { responseType: 'text' })).then(
      () => undefined
    );
  }
}
