import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';

export type SupportedLanguage = 'en' | 'ar';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly LANG_KEY = 'layla_lang';
  private currentLang$ = new BehaviorSubject<SupportedLanguage>('en');

  readonly lang$ = this.currentLang$.asObservable();

  constructor(private translate: TranslateService) {}

  init(): void {
    const saved = localStorage.getItem(this.LANG_KEY) as SupportedLanguage | null;
    const lang: SupportedLanguage = saved === 'ar' ? 'ar' : 'en';
    this.translate.addLangs(['en', 'ar']);
    this.translate.setDefaultLang('en');
    this.setLanguage(lang);
  }

  setLanguage(lang: SupportedLanguage): void {
    this.translate.use(lang);
    this.currentLang$.next(lang);
    localStorage.setItem(this.LANG_KEY, lang);
    const html = document.getElementById('html-root') || document.documentElement;
    html.setAttribute('lang', lang);
    html.setAttribute('dir', lang === 'ar' ? 'rtl' : 'ltr');
    document.body.classList.toggle('rtl', lang === 'ar');
  }

  toggleLanguage(): void {
    this.setLanguage(this.currentLang$.value === 'en' ? 'ar' : 'en');
  }

  get currentLang(): SupportedLanguage {
    return this.currentLang$.value;
  }

  get isRtl(): boolean {
    return this.currentLang$.value === 'ar';
  }
}
