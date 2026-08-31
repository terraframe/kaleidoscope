import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    localStorage.removeItem('geo-ai-explorer-theme');
    document.documentElement.classList.remove('app-dark');

    await TestBed.configureTestingModule({
      imports: [AppComponent],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should toggle and persist dark mode', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const initialMode = app.isDarkMode;

    app.toggleTheme();

    expect(app.isDarkMode).toBe(!initialMode);
    expect(document.documentElement.classList.contains('app-dark')).toBe(!initialMode);
    expect(localStorage.getItem('geo-ai-explorer-theme')).toBe(!initialMode ? 'dark' : 'light');
  });
});
