import { NgIf } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
    selector: 'app-root',
    imports: [RouterOutlet, NgIf],
    templateUrl: './app.component.html',
    styleUrl: './app.component.scss'
})
export class AppComponent implements OnDestroy {
    private readonly storageKey = 'geo-ai-explorer-theme';
    private readonly systemTheme = window.matchMedia('(prefers-color-scheme: dark)');
    private readonly onSystemThemeChange = (event: MediaQueryListEvent): void => {
        if (!localStorage.getItem(this.storageKey)) {
            this.setDarkMode(event.matches);
        }
    };

    isDarkMode = document.documentElement.classList.contains('app-dark');

    constructor() {
        const savedTheme = localStorage.getItem(this.storageKey);
        this.setDarkMode(savedTheme ? savedTheme === 'dark' : this.systemTheme.matches);
        this.systemTheme.addEventListener('change', this.onSystemThemeChange);
    }

    toggleTheme(): void {
        this.setDarkMode(!this.isDarkMode);
        localStorage.setItem(this.storageKey, this.isDarkMode ? 'dark' : 'light');
    }

    ngOnDestroy(): void {
        this.systemTheme.removeEventListener('change', this.onSystemThemeChange);
    }

    private setDarkMode(enabled: boolean): void {
        this.isDarkMode = enabled;
        document.documentElement.classList.toggle('app-dark', enabled);
        document.documentElement.style.colorScheme = enabled ? 'dark' : 'light';
    }
}
