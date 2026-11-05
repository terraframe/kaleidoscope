import { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideStore } from '@ngrx/store';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeuix/themes/aura';

import { chatReducer } from '../state/chat.state';
import { explorerReducer } from '../state/explorer.state';

/**
 * Common providers for component specs. Mirrors app.config.ts, but swaps in
 * test-safe versions (no-op animations, HttpTestingController-backed HTTP).
 */
export function provideTestApp(): (Provider | EnvironmentProviders)[] {
    return [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        providePrimeNG({ theme: { preset: Aura, options: { darkModeSelector: '.app-dark' } } }),
        provideStore({
            chat: chatReducer,
            explorer: explorerReducer
        }),
        MessageService,
    ];
}
