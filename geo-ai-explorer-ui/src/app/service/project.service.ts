import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { Project } from '../models/chat.model';
import { apiBase } from './url.util';

@Injectable({
  providedIn: 'root',
})
export class ProjectService {
  constructor(private http: HttpClient) {}

  getAll(): Promise<Project[]> {
    return firstValueFrom(this.http.get<Project[]>(apiBase() + 'api/projects'));
  }

  create(name: string): Promise<Project> {
    return firstValueFrom(this.http.post<Project>(apiBase() + 'api/projects', { name }));
  }
}
