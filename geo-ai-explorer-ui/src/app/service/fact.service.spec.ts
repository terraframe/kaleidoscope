import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { FactService } from './fact.service';
import { Fact } from '../models/chat.model';
import { apiBase } from './url.util';

describe('FactService', () => {
  let service: FactService;
  let httpTesting: HttpTestingController;

  const fact: Fact = {
    name: 'Population',
    description: 'Population of the selected region',
    text: 'The population is 1,000.',
    statements: ['statement-1', 'statement-2'],
    projectId: 3,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(FactService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('save posts the fact and resolves with the saved fact', async () => {
    const saved: Fact = { ...fact, id: 42 };

    const promise = service.save(fact);

    const req = httpTesting.expectOne(apiBase() + 'api/facts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(fact);
    req.flush(saved);

    expect(await promise).toEqual(saved);
  });

  it('save sends the project name when creating a new project', async () => {
    const newProjectFact: Fact = { ...fact, projectId: null, projectName: 'New One' };

    const promise = service.save(newProjectFact);

    const req = httpTesting.expectOne(apiBase() + 'api/facts');
    expect(req.request.body.projectId).toBeNull();
    expect(req.request.body.projectName).toBe('New One');
    req.flush({ ...newProjectFact, id: 7, projectId: 9 });

    expect((await promise).projectId).toBe(9);
  });

  it('save rejects when the request fails', async () => {
    const promise = service.save(fact);

    httpTesting
      .expectOne(apiBase() + 'api/facts')
      .flush('Bad request', { status: 400, statusText: 'Bad Request' });

    await expectAsync(promise).toBeRejected();
  });
});
