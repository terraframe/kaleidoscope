import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ChatService } from './chat-service.service';
import { apiBase } from './url.util';

describe('ChatServiceService', () => {
  let service: ChatService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ChatService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('sends the prompt in a POST body', () => {
    const prompt = 'SELECT ?s ?p ?o WHERE { ?s ?p ?o } ' + 'x'.repeat(20000);

    service.sendMessage('session-1', { id: 'm1', sender: 'user', text: prompt, mappable: false, purpose: 'standard', statements: [] });

    const request = httpTesting.expectOne(apiBase() + 'api/chat/prompt/start');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ sessionId: 'session-1', prompt: prompt });
    expect(request.request.urlWithParams).not.toContain('prompt=');
  });
});
