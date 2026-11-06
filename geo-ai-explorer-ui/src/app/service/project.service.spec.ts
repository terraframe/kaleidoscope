import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ProjectService } from './project.service';
import { Project } from '../models/chat.model';
import { apiBase } from './url.util';

describe('ProjectService', () => {
  let service: ProjectService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ProjectService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getAll fetches projects from the projects endpoint', async () => {
    const projects: Project[] = [
      { id: 1, name: 'Alpha' },
      { id: 2, name: 'Beta' },
    ];

    const promise = service.getAll();

    const req = httpTesting.expectOne(apiBase() + 'api/projects');
    expect(req.request.method).toBe('GET');
    req.flush(projects);

    expect(await promise).toEqual(projects);
  });

  it('getAll resolves to an empty array when there are no projects', async () => {
    const promise = service.getAll();

    httpTesting.expectOne(apiBase() + 'api/projects').flush([]);

    expect(await promise).toEqual([]);
  });

  it('getAll rejects when the request fails', async () => {
    const promise = service.getAll();

    httpTesting
      .expectOne(apiBase() + 'api/projects')
      .flush('Server error', { status: 500, statusText: 'Internal Server Error' });

    await expectAsync(promise).toBeRejected();
  });
});
