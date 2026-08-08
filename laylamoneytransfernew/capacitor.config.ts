import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.remitz.app',
  appName: 'Remitz',
  webDir: 'www',
  server: {
    androidScheme: 'https',
    // Keep the Trust Payments hosted page INSIDE the app WebView. Without this, a cross-origin
    // form POST to securetrading.net is handed off to the external system browser as a GET,
    // dropping the POST body — Trust then sees no fields and shows "Missing" (mobile card failures).
    // Whitelisting the domain lets the WebView navigate there in-app so the POST body is preserved,
    // and Trust's redirect back to the app origin (localhost) resolves in-WebView.
    allowNavigation: [
      'payments.securetrading.net',
      '*.securetrading.net'
    ]
  }
};

export default config;
