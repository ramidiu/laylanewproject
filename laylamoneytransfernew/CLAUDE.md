# Layla Money Transfer — Frontend

Angular 14 / Ionic 6 frontend for Layla Money Transfer.
Rebranded from the RemitzGlobal / ForexBridge project.

## Brand
- Primary: `#1B3A6B` (deep navy)
- Accent: `#C0203E` (crimson red)
- Design tokens: `src/theme/remitzai-design-tokens.scss` (variable names kept as `$fb-` for backward compat)
- Fonts: Inter (English), IBM Plex Sans Arabic (Arabic)

## Stack
Angular 14, Ionic 6, SCSS, ngx-translate, Capacitor 5

## Key Files
- `src/theme/remitzai-design-tokens.scss` — all brand colors, spacing, typography
- `src/theme/variables.scss` — Ionic CSS custom property overrides
- `src/global.scss` — global styles + Arabic RTL support
- `src/assets/i18n/en.json` — English translations
- `src/assets/i18n/ar.json` — Arabic translations
- `src/app/core/services/language.service.ts` — RTL/language switching

## Ports
| Service | URL |
|---------|-----|
| Dev server | http://localhost:8100 |

## Commands
```bash
cd laylamoneytransfernew
npm install
npm start             # dev server on :8100
npm run build         # production build → www/
```

## i18n / RTL
- Language persisted in `localStorage` key: `layla_lang`
- Switching: `LanguageService.setLanguage('ar')` / `toggleLanguage()`
- Arabic automatically sets `dir="rtl"` on `<html>` and adds `.rtl` class to `<body>`
- All layout components have a `EN / عر` toggle button in the toolbar

## Do-Not-Touch
- `src/app/core/guards/` — auth/role guards
- `src/app/core/interceptors/` — JWT, error handling
- All service API calls in `src/app/core/services/`
- Business logic in all page TypeScript files
- Route definitions in `*-routing.module.ts` files
