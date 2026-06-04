import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.remitz.app',
  appName: 'Remitz',
  webDir: 'www',
  server: {
    androidScheme: 'https'
  }
};

export default config;
