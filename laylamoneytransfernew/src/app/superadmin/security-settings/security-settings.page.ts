import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { ConfigService } from '../../core/services/config.service';

interface SecurityConfig {
  key: string;
  value: string;
  label: string;
  description: string;
  type: 'boolean' | 'number' | 'text' | 'textarea';
  group: string;
}

@Component({
  selector: 'app-security-settings',
  template: `
    <div class="security-settings">
      <div class="page-header">
        <div>
          <h1 class="page-title">Security Settings</h1>
          <p class="page-subtitle">Manage IP allowlist, rate limiting, and login security</p>
        </div>
      </div>

      <div *ngIf="loading" class="loading-state">
        <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
      </div>

      <div *ngIf="!loading">
        <!-- IP Allowlist Section -->
        <div class="fb-card settings-section">
          <div class="section-header">
            <div>
              <h2 class="section-title">
                <ion-icon name="shield-checkmark-outline"></ion-icon>
                Admin IP Allowlist
              </h2>
              <p class="section-desc">Restrict admin panel access to specific IP addresses</p>
            </div>
          </div>
          <div class="settings-grid">
            <div class="setting-row" *ngFor="let config of getGroup('ip_allowlist')">
              <div class="setting-info">
                <label class="setting-label">{{ config.label }}</label>
                <span class="setting-desc">{{ config.description }}</span>
              </div>
              <div class="setting-control">
                <div *ngIf="config.type === 'boolean'" class="toggle-wrapper">
                  <ion-toggle
                    [checked]="config.value === 'true'"
                    (ionChange)="onToggle(config, $event)"
                    mode="ios"
                  ></ion-toggle>
                  <span class="toggle-label">{{ config.value === 'true' ? 'Enabled' : 'Disabled' }}</span>
                </div>
                <textarea
                  *ngIf="config.type === 'textarea'"
                  class="fb-input setting-textarea"
                  [value]="config.value"
                  (blur)="onInputChange(config, $event)"
                  rows="4"
                  placeholder="Enter one IP per line"
                ></textarea>
                <input
                  *ngIf="config.type === 'text'"
                  class="fb-input"
                  [value]="config.value"
                  (blur)="onInputChange(config, $event)"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Login Security Section -->
        <div class="fb-card settings-section">
          <div class="section-header">
            <div>
              <h2 class="section-title">
                <ion-icon name="lock-closed-outline"></ion-icon>
                Login Security
              </h2>
              <p class="section-desc">Configure login attempts, lockout, and session limits</p>
            </div>
          </div>
          <div class="settings-grid">
            <div class="setting-row" *ngFor="let config of getGroup('login_security')">
              <div class="setting-info">
                <label class="setting-label">{{ config.label }}</label>
                <span class="setting-desc">{{ config.description }}</span>
              </div>
              <div class="setting-control">
                <div *ngIf="config.type === 'boolean'" class="toggle-wrapper">
                  <ion-toggle
                    [checked]="config.value === 'true'"
                    (ionChange)="onToggle(config, $event)"
                    mode="ios"
                  ></ion-toggle>
                  <span class="toggle-label">{{ config.value === 'true' ? 'Enabled' : 'Disabled' }}</span>
                </div>
                <input
                  *ngIf="config.type === 'number'"
                  type="number"
                  class="fb-input setting-number"
                  [value]="config.value"
                  (blur)="onInputChange(config, $event)"
                  min="1"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Rate Limiting Section -->
        <div class="fb-card settings-section">
          <div class="section-header">
            <div>
              <h2 class="section-title">
                <ion-icon name="speedometer-outline"></ion-icon>
                Rate Limiting
              </h2>
              <p class="section-desc">Control API request rates per endpoint</p>
            </div>
          </div>
          <div class="settings-grid">
            <div class="setting-row" *ngFor="let config of getGroup('rate_limit')">
              <div class="setting-info">
                <label class="setting-label">{{ config.label }}</label>
                <span class="setting-desc">{{ config.description }}</span>
              </div>
              <div class="setting-control">
                <input
                  *ngIf="config.type === 'number'"
                  type="number"
                  class="fb-input setting-number"
                  [value]="config.value"
                  (blur)="onInputChange(config, $event)"
                  min="0"
                  step="0.1"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- MFA Section -->
        <div class="fb-card settings-section">
          <div class="section-header">
            <div>
              <h2 class="section-title">
                <ion-icon name="key-outline"></ion-icon>
                Multi-Factor Authentication
              </h2>
              <p class="section-desc">MFA enforcement for staff roles</p>
            </div>
          </div>
          <div class="settings-grid">
            <div class="setting-row" *ngFor="let config of getGroup('mfa')">
              <div class="setting-info">
                <label class="setting-label">{{ config.label }}</label>
                <span class="setting-desc">{{ config.description }}</span>
              </div>
              <div class="setting-control">
                <div *ngIf="config.type === 'boolean'" class="toggle-wrapper">
                  <ion-toggle
                    [checked]="config.value === 'true'"
                    (ionChange)="onToggle(config, $event)"
                    mode="ios"
                  ></ion-toggle>
                  <span class="toggle-label">{{ config.value === 'true' ? 'Enforced' : 'Disabled' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="info-banner">
          <ion-icon name="information-circle-outline"></ion-icon>
          <span>Some settings require a service restart to take effect. Changes are saved immediately to the database.</span>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./security-settings.page.scss']
})
export class SecuritySettingsPage implements OnInit {
  configs: SecurityConfig[] = [];
  loading = true;

  private defaultConfigs: SecurityConfig[] = [
    { key: 'security.ip_allowlist.enabled', value: 'false', label: 'Enable IP Allowlist', description: 'When enabled, only listed IPs can access admin endpoints', type: 'boolean', group: 'ip_allowlist' },
    { key: 'security.ip_allowlist.allowed_ips', value: '', label: 'Allowed IP Addresses', description: 'One IP address per line. Supports exact IPs and CIDR prefixes (e.g. 10.0.0.)', type: 'textarea', group: 'ip_allowlist' },
    { key: 'security.ip_allowlist.protected_paths', value: '/api/auth/admin/**\n/api/compliance/admin/**\n/api/users/admin/**\n/api/transactions/admin/**', label: 'Protected Paths', description: 'API paths protected by IP allowlist (one per line)', type: 'textarea', group: 'ip_allowlist' },
    { key: 'security.login.max_attempts', value: '5', label: 'Max Login Attempts', description: 'Number of failed login attempts before account lockout', type: 'number', group: 'login_security' },
    { key: 'security.login.lockout_duration_minutes', value: '30', label: 'Lockout Duration (minutes)', description: 'How long an account stays locked after exceeding max attempts', type: 'number', group: 'login_security' },
    { key: 'security.login.max_concurrent_sessions', value: '3', label: 'Max Concurrent Sessions', description: 'Maximum simultaneous login sessions per user', type: 'number', group: 'login_security' },
    { key: 'security.login.password_history_count', value: '5', label: 'Password History Count', description: 'Number of previous passwords to prevent reuse', type: 'number', group: 'login_security' },
    { key: 'security.rate_limit.default_rate', value: '50', label: 'Default Rate (req/sec)', description: 'Default API rate limit for all endpoints', type: 'number', group: 'rate_limit' },
    { key: 'security.rate_limit.default_burst', value: '100', label: 'Default Burst', description: 'Maximum burst capacity for token bucket', type: 'number', group: 'rate_limit' },
    { key: 'security.rate_limit.login_rate', value: '0.2', label: 'Login Rate (req/sec)', description: 'Rate limit for login endpoint (0.2 = 1 request per 5 seconds)', type: 'number', group: 'rate_limit' },
    { key: 'security.rate_limit.register_rate', value: '0.1', label: 'Registration Rate (req/sec)', description: 'Rate limit for registration endpoint', type: 'number', group: 'rate_limit' },
    { key: 'security.rate_limit.fx_quote_rate', value: '1.0', label: 'FX Quote Rate (req/sec)', description: 'Rate limit for FX quote requests', type: 'number', group: 'rate_limit' },
    { key: 'security.rate_limit.transaction_rate', value: '0.5', label: 'Transaction Rate (req/sec)', description: 'Rate limit for creating transactions', type: 'number', group: 'rate_limit' },
    { key: 'security.mfa.enforce_staff', value: 'false', label: 'Enforce MFA for Staff', description: 'Require MFA setup for Admin, Compliance, Treasury, Support, Finance, and Partner roles', type: 'boolean', group: 'mfa' },
  ];

  constructor(
    private configService: ConfigService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadConfigs();
  }

  loadConfigs(): void {
    this.loading = true;
    this.configService.getSystemConfig().subscribe({
      next: (res: any) => {
        const saved = Array.isArray(res) ? res : (res?.data || res?.content || []);
        this.configs = this.defaultConfigs.map(def => {
          const found = saved.find((s: any) => s.configKey === def.key);
          return { ...def, value: found?.configValue ?? def.value };
        });
        this.loading = false;
      },
      error: () => {
        this.configs = [...this.defaultConfigs];
        this.loading = false;
      }
    });
  }

  getGroup(group: string): SecurityConfig[] {
    return this.configs.filter(c => c.group === group);
  }

  onToggle(config: SecurityConfig, event: any): void {
    config.value = event.detail.checked ? 'true' : 'false';
    this.saveConfig(config);
  }

  onInputChange(config: SecurityConfig, event: any): void {
    const newValue = event.target.value;
    if (newValue !== config.value) {
      config.value = newValue;
      this.saveConfig(config);
    }
  }

  saveConfig(config: SecurityConfig): void {
    this.configService.updateSystemConfig(config.key, config.value).subscribe({
      next: () => {
        this.showToast(`${config.label} updated`, 'success');
      },
      error: () => {
        this.showToast(`Failed to update ${config.label}`, 'danger');
      }
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, duration: 3000, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
