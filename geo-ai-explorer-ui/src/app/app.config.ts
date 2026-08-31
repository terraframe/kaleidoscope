import { ApplicationConfig } from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideStore } from '@ngrx/store';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeng/themes/aura';

import { routes } from './app.routes';
import { chatReducer } from './state/chat.state';
import { MessageService } from 'primeng/api';
import { explorerReducer } from './state/explorer.state';
import { authInterceptor } from './service/auth-interceptor.service';

export const appConfig: ApplicationConfig = {
    providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideRouter(routes),
        provideAnimations(),
        provideAnimationsAsync(),
        providePrimeNG({
            theme: {
                preset: Aura,
                options: {
                    darkModeSelector: '.app-dark'
                }
            }
        }),
        provideStore({
            chat: chatReducer,
            explorer: explorerReducer
        }),
        MessageService,
    ]
};
