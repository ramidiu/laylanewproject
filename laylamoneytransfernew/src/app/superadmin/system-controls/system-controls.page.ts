// Code added by Naresh: System Controls Phase 6 — UI polish on the existing
// superadmin/system-controls page. Renders every row from /api/users/admin/system-config
// using a type-aware editor (BOOLEAN/INT/DECIMAL/STRING/JSON), with search, per-key
// history drawer and inline backend-validation error surfacing.

import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { AlertController, ToastController } from '@ionic/angular';
import { environment } from '../../../environments/environment';

type ValueType = 'BOOLEAN' | 'INT' | 'DECIMAL' | 'STRING' | 'JSON';

interface ConfigRow {
  key: string;
  value: string;            // raw DB value
  draft: string;            // editor working copy (string form for every type)
  draftBool: boolean;       // editor working copy for BOOLEAN
  valueType: ValueType;
  category: string;
  allowedValues: string[] | null;
  version: number;
  description: string;
  updatedBy: string | null;
  updatedAt: string | null;

  dirty: boolean;           // true when draft differs from value
  saving: boolean;
  error: string | null;     // last backend validation/conflict message
  // Code added by Naresh: System Controls Phase 7 — operator reason for dangerous toggles.
  pendingReason: string | null;
}

interface AuditRow {
  id: number;
  oldValue: string | null;
  newValue: string | null;
  oldVersion: number | null;
  newVersion: number | null;
  changedBy: string;
  changedAt: string;
  changeSource: string;
  reason: string | null;
}

@Component({
  selector: 'app-system-controls',
  template: `
    <div class="controls-page">
      <!-- ============ HEADER ============ -->
      <div class="page-header">
        <div>
          <h1 class="page-title">System Controls</h1>
          <p class="page-subtitle">Runtime configuration — changes take effect immediately</p>
        </div>
        <div class="header-actions">
          <div class="search-wrap">
            <ion-icon name="search-outline"></ion-icon>
            <input class="search-input" type="text" placeholder="Search key, description or category…"
                   [(ngModel)]="searchTerm" (ngModelChange)="applyFilter()" />
            <button *ngIf="searchTerm" class="clear-btn" (click)="searchTerm=''; applyFilter()" aria-label="Clear">
              <ion-icon name="close-outline"></ion-icon>
            </button>
          </div>
          <button class="fb-btn fb-btn--secondary" (click)="loadRows()">
            <ion-icon name="refresh-outline"></ion-icon> Refresh
          </button>
        </div>
      </div>

      <!-- ============ LOADING / EMPTY ============ -->
      <div *ngIf="loading" class="loading">Loading controls…</div>
      <div *ngIf="!loading && visibleCategories.length === 0" class="loading">
        No configuration rows match the current filter.
      </div>

      <!-- ============ CATEGORIES ============ -->
      <ng-container *ngIf="!loading">
        <div *ngFor="let cat of visibleCategories" class="category-section">
          <div class="category-header">
            <ion-icon [name]="catIcons[cat] || 'settings-outline'"></ion-icon>
            <h2>{{ catLabels[cat] || cat }}</h2>
            <span class="category-count">{{ getVisibleRows(cat).length }} of {{ getCategoryRows(cat).length }}</span>
          </div>
          <div class="cfg-list">
            <div *ngFor="let row of getVisibleRows(cat)"
                 class="cfg-row"
                 [class.cfg-row--danger]="isDanger(row)">
              <div class="cfg-row__main">
                <div class="cfg-row__label">
                  <ion-icon *ngIf="isDanger(row)" name="warning-outline" class="danger-ico"
                            title="High-impact control — confirmation required"></ion-icon>
                  {{ humanLabel(row.key) }}
                  <span class="cfg-row__type-badge type-{{ row.valueType.toLowerCase() }}">{{ row.valueType }}</span>
                  <span class="cfg-row__ver-badge" title="Version">v{{ row.version }}</span>
                  <span *ngIf="isDanger(row)" class="cfg-row__danger-badge">Critical</span>
                </div>
                <div class="cfg-row__desc" *ngIf="row.description">{{ row.description }}</div>
                <div class="cfg-row__key">{{ row.key }}</div>
                <div class="cfg-row__error" *ngIf="row.error">
                  <ion-icon name="alert-circle-outline"></ion-icon>
                  {{ row.error }}
                </div>
              </div>

              <!-- ============ EDITOR ============ -->
              <div class="cfg-row__editor">
                <!-- BOOLEAN: toggle auto-saves on change -->
                <ng-container *ngIf="row.valueType === 'BOOLEAN'">
                  <label class="switch">
                    <input type="checkbox" [checked]="row.draftBool"
                           (change)="toggleBool(row)" [disabled]="row.saving" />
                    <span class="slider"></span>
                  </label>
                  <span class="cfg-row__status" [class.on]="row.draftBool" [class.off]="!row.draftBool">
                    {{ row.draftBool ? 'ON' : 'OFF' }}
                  </span>
                </ng-container>

                <!-- INT -->
                <input *ngIf="row.valueType === 'INT'" type="number" step="1"
                       class="cfg-input"
                       [(ngModel)]="row.draft" (ngModelChange)="markDirty(row)"
                       [disabled]="row.saving" />

                <!-- DECIMAL -->
                <input *ngIf="row.valueType === 'DECIMAL'" type="number" step="0.01"
                       class="cfg-input"
                       [(ngModel)]="row.draft" (ngModelChange)="markDirty(row)"
                       [disabled]="row.saving" />

                <!-- STRING with optional allowed_values as a <select> -->
                <ng-container *ngIf="row.valueType === 'STRING'">
                  <select *ngIf="row.allowedValues && row.allowedValues.length" class="cfg-input"
                          [(ngModel)]="row.draft" (ngModelChange)="markDirty(row)"
                          [disabled]="row.saving">
                    <option *ngFor="let opt of row.allowedValues" [value]="opt">{{ opt }}</option>
                  </select>
                  <input *ngIf="!row.allowedValues || !row.allowedValues.length" type="text"
                         class="cfg-input"
                         [(ngModel)]="row.draft" (ngModelChange)="markDirty(row)"
                         [disabled]="row.saving" />
                </ng-container>

                <!-- JSON: textarea -->
                <textarea *ngIf="row.valueType === 'JSON'" rows="3"
                          class="cfg-input cfg-input--json"
                          [(ngModel)]="row.draft" (ngModelChange)="markDirty(row)"
                          [disabled]="row.saving"></textarea>

                <!-- Save + History buttons (not shown for BOOLEAN) -->
                <div class="cfg-row__actions" *ngIf="row.valueType !== 'BOOLEAN'">
                  <button class="save-btn" (click)="saveRow(row)"
                          [disabled]="!row.dirty || row.saving">
                    <ion-icon *ngIf="!row.saving" name="save-outline"></ion-icon>
                    <ion-icon *ngIf="row.saving" name="sync-outline" class="spin"></ion-icon>
                    {{ row.saving ? 'Saving…' : 'Save' }}
                  </button>
                  <button class="revert-btn" *ngIf="row.dirty && !row.saving"
                          (click)="revertRow(row)" aria-label="Revert">
                    <ion-icon name="arrow-undo-outline"></ion-icon>
                  </button>
                </div>

                <button class="history-btn" (click)="openHistory(row)" title="Change history">
                  <ion-icon name="time-outline"></ion-icon>
                </button>
              </div>
            </div>
          </div>
        </div>
      </ng-container>

      <!-- ============ HISTORY DRAWER ============ -->
      <div class="drawer-backdrop" *ngIf="historyOpen" (click)="closeHistory()"></div>
      <aside class="history-drawer" [class.history-drawer--open]="historyOpen">
        <div class="history-header">
          <div>
            <div class="history-title">Change History</div>
            <div class="history-sub">{{ historyKey }}</div>
          </div>
          <button class="close-btn" (click)="closeHistory()" aria-label="Close">
            <ion-icon name="close-outline"></ion-icon>
          </button>
        </div>
        <div class="history-body">
          <div *ngIf="historyLoading" class="loading">Loading history…</div>
          <div *ngIf="!historyLoading && history.length === 0" class="loading">
            No changes recorded yet.
          </div>
          <ul class="history-list" *ngIf="!historyLoading && history.length">
            <li *ngFor="let h of history" class="history-item">
              <div class="history-item__meta">
                <span class="history-item__ts">{{ h.changedAt | date:'medium' }}</span>
                <span class="history-item__src">{{ h.changeSource }}</span>
              </div>
              <div class="history-item__diff">
                <span class="old">{{ h.oldValue ?? '—' }}</span>
                <ion-icon name="arrow-forward-outline"></ion-icon>
                <span class="new">{{ h.newValue ?? '—' }}</span>
              </div>
              <div class="history-item__footer">
                <span>v{{ h.oldVersion }} → v{{ h.newVersion }}</span>
                <span class="history-item__by">{{ h.changedBy }}</span>
              </div>
              <div class="history-item__reason" *ngIf="h.reason">
                <ion-icon name="chatbox-ellipses-outline"></ion-icon>
                {{ h.reason }}
              </div>
            </li>
          </ul>
        </div>
      </aside>
    </div>
  `,
  styleUrls: ['./system-controls.page.scss']
})
export class SystemControlsPage implements OnInit {
  rows: ConfigRow[] = [];
  visibleRows: ConfigRow[] = [];
  categories: string[] = [];
  visibleCategories: string[] = [];
  loading = true;
  searchTerm = '';

  // Audit drawer state
  historyOpen = false;
  historyLoading = false;
  historyKey: string | null = null;
  history: AuditRow[] = [];

  catLabels: Record<string, string> = {
    'operations': 'Global Operations',
    'maintenance': 'Maintenance Mode',
    'compliance': 'Compliance & Screening',
    'kyc': 'KYC Controls',
    'transaction': 'Transaction Controls',
    'routing': 'Pay-In / Pay-Out Routing',
    'notifications': 'Notifications',
    'security': 'Security',
    'fx': 'FX & Fees',
    'jobs': 'Scheduled Jobs',
    'general': 'General'
  };

  catIcons: Record<string, string> = {
    'operations': 'globe-outline',
    'maintenance': 'construct-outline',
    'compliance': 'shield-checkmark-outline',
    'kyc': 'document-text-outline',
    'transaction': 'swap-horizontal-outline',
    'routing': 'git-network-outline',
    'notifications': 'notifications-outline',
    'security': 'lock-closed-outline',
    'fx': 'trending-up-outline',
    'jobs': 'timer-outline',
    'general': 'cog-outline'
  };

  // Code added by Naresh: System Controls Phase 7 — keys flagged as high-impact
  // trigger a confirmation modal with an optional "reason" textbox before saving.
  private dangerKeys = new Set<string>([
    'transactions.enabled',
    'compliance.enabled',
    'payin.enabled',
    'payout.enabled',
    'maintenance.mode.enabled'
  ]);

  constructor(private http: HttpClient,
              private toastCtrl: ToastController,
              private alertCtrl: AlertController) {}

  ngOnInit(): void { this.loadRows(); }

  // ========== LOAD ==========
  loadRows(): void {
    this.loading = true;
    this.http.get<any>(`${environment.apiUrl}/users/admin/system-config`).subscribe({
      next: (res: any) => {
        const data: any[] = Array.isArray(res) ? res : (res?.data || []);
        this.rows = data.map((c: any) => this.toConfigRow(c))
                        .sort((a, b) => a.key.localeCompare(b.key));
        this.rebuildCategories();
        this.applyFilter();
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.rows = [];
        this.visibleRows = [];
        this.visibleCategories = [];
        this.loading = false;
        this.showToast(`Failed to load system controls: ${this.describeError(err)}`, 'danger');
      }
    });
  }

  private toConfigRow(c: any): ConfigRow {
    const valueType = (c.valueType || 'STRING').toUpperCase() as ValueType;
    const rawValue = c.configValue ?? '';
    let allowed: string[] | null = null;
    if (c.allowedValues) {
      try { allowed = JSON.parse(c.allowedValues); } catch { allowed = null; }
    }
    return {
      key: c.configKey,
      value: rawValue,
      draft: rawValue,
      draftBool: rawValue === 'true',
      valueType,
      category: c.category || 'general',
      allowedValues: Array.isArray(allowed) ? allowed : null,
      version: Number(c.version ?? 1),
      description: c.description || '',
      updatedBy: c.updatedBy ?? null,
      updatedAt: c.updatedAt ?? null,
      dirty: false,
      saving: false,
      error: null,
      pendingReason: null
    };
  }

  // Code added by Naresh: Phase 7 — confirmation alert with a "reason" textbox.
  // Returns true when the operator confirmed (and the reason, if any, is stored
  // on row.pendingReason to flow into the subsequent PUT).
  private async confirmDangerChange(row: ConfigRow, nextValue: string): Promise<boolean> {
    return new Promise(resolve => {
      this.alertCtrl.create({
        header: 'Confirm critical change',
        message: `<strong>${row.key}</strong><br><br>`
               + `This control is flagged high-impact. New value:<br>`
               + `<code>${nextValue}</code><br><br>`
               + `Please provide a reason for the audit trail.`,
        inputs: [{
          name: 'reason',
          type: 'textarea',
          placeholder: 'Reason (e.g. "Emergency cutover 2026-04-24")'
        }],
        buttons: [
          { text: 'Cancel', role: 'cancel', handler: () => resolve(false) },
          {
            text: 'Confirm',
            cssClass: 'alert-confirm-danger',
            handler: (data: any) => {
              row.pendingReason = data?.reason ? String(data.reason).trim() : null;
              resolve(true);
            }
          }
        ]
      }).then(alert => alert.present());
    });
  }

  private rebuildCategories(): void {
    // Code added by Naresh: Phase 7 — operations + maintenance lead the list so the
    // most impactful controls surface first.
    const order = ['operations','maintenance','compliance','kyc','transaction','routing','notifications','security','fx','jobs','general'];
    const present = new Set(this.rows.map(r => r.category));
    this.categories = [];
    for (const c of order) if (present.has(c)) this.categories.push(c);
    for (const c of Array.from(present)) if (!this.categories.includes(c)) this.categories.push(c);
  }

  isDanger(row: ConfigRow): boolean {
    return this.dangerKeys.has(row.key);
  }

  // ========== SEARCH / FILTER ==========
  applyFilter(): void {
    const q = this.searchTerm.trim().toLowerCase();
    this.visibleRows = !q
      ? this.rows.slice()
      : this.rows.filter(r =>
          r.key.toLowerCase().includes(q) ||
          (r.description || '').toLowerCase().includes(q) ||
          r.category.toLowerCase().includes(q));
    const visibleCats = new Set(this.visibleRows.map(r => r.category));
    this.visibleCategories = this.categories.filter(c => visibleCats.has(c));
  }

  getCategoryRows(cat: string): ConfigRow[] {
    return this.rows.filter(r => r.category === cat);
  }
  getVisibleRows(cat: string): ConfigRow[] {
    return this.visibleRows.filter(r => r.category === cat);
  }

  // ========== EDIT / SAVE ==========
  markDirty(row: ConfigRow): void {
    row.dirty = row.draft !== row.value;
    row.error = null;
  }

  revertRow(row: ConfigRow): void {
    row.draft = row.value;
    row.draftBool = row.value === 'true';
    row.dirty = false;
    row.error = null;
  }

  toggleBool(row: ConfigRow): void {
    const next = !row.draftBool;
    if (this.isDanger(row)) {
      // Confirmation modal with required reason textbox. If the operator cancels
      // we revert the local toggle so the UI does not lie.
      this.confirmDangerChange(row, String(next)).then(proceed => {
        if (!proceed) {
          // Snap the switch back to the persisted value.
          row.draftBool = row.value === 'true';
          return;
        }
        row.draftBool = next;
        row.draft = String(next);
        row.dirty = true;
        this.saveRow(row);
      });
      return;
    }
    row.draftBool = next;
    row.draft = String(next);
    row.dirty = true;
    this.saveRow(row);
  }

  saveRow(row: ConfigRow): void {
    if (row.saving || !row.dirty) return;

    // For non-boolean danger rows, require confirmation before saving.
    if (this.isDanger(row) && row.valueType !== 'BOOLEAN' && !row.pendingReason) {
      this.confirmDangerChange(row, row.draft).then(proceed => {
        if (!proceed) return;
        this.saveRow(row);
      });
      return;
    }

    row.saving = true;
    row.error = null;

    const body: any = { value: row.draft, version: row.version };
    if (row.pendingReason) body.reason = row.pendingReason;
    row.pendingReason = null;

    this.http.put<any>(
      `${environment.apiUrl}/users/admin/system-config/${row.key}`,
      body
    ).subscribe({
      next: (res: any) => {
        const saved = res?.data ?? res;
        row.value = saved?.configValue ?? row.draft;
        row.draft = row.value;
        row.draftBool = row.value === 'true';
        row.version = Number(saved?.version ?? row.version + 1);
        row.updatedBy = saved?.updatedBy ?? row.updatedBy;
        row.updatedAt = saved?.updatedAt ?? row.updatedAt;
        row.dirty = false;
        row.saving = false;
        this.showToast(`${this.humanLabel(row.key)} saved`, 'success');
      },
      error: (err: HttpErrorResponse) => {
        row.saving = false;
        if (err.status === 409) {
          row.error = 'This control was changed elsewhere. Refresh to load the latest value before editing again.';
          // Revert the local boolean state so UI doesn't lie
          if (row.valueType === 'BOOLEAN') row.draftBool = row.value === 'true';
          this.showToast('Version conflict — refresh to see the latest', 'warning');
        } else if (err.status === 400) {
          row.error = this.describeError(err) || 'Invalid value';
          if (row.valueType === 'BOOLEAN') row.draftBool = row.value === 'true';
          this.showToast('Validation failed — see row for details', 'danger');
        } else if (err.status === 401 || err.status === 403) {
          row.error = 'You do not have permission to change this control.';
          if (row.valueType === 'BOOLEAN') row.draftBool = row.value === 'true';
          this.showToast('Not authorised', 'danger');
        } else {
          row.error = this.describeError(err);
          if (row.valueType === 'BOOLEAN') row.draftBool = row.value === 'true';
          this.showToast('Failed to save', 'danger');
        }
      }
    });
  }

  // ========== HISTORY DRAWER ==========
  openHistory(row: ConfigRow): void {
    this.historyKey = row.key;
    this.history = [];
    this.historyOpen = true;
    this.historyLoading = true;
    this.http.get<any>(
      `${environment.apiUrl}/users/admin/system-config/${row.key}/history`
    ).subscribe({
      next: (res: any) => {
        const data: any[] = Array.isArray(res) ? res : (res?.data || []);
        this.history = data.map((a: any) => ({
          id: a.id,
          oldValue: a.oldValue,
          newValue: a.newValue,
          oldVersion: a.oldVersion,
          newVersion: a.newVersion,
          changedBy: a.changedBy,
          changedAt: a.changedAt,
          changeSource: a.changeSource,
          reason: a.reason ?? null
        }));
        this.historyLoading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.historyLoading = false;
        this.showToast(`Failed to load history: ${this.describeError(err)}`, 'danger');
      }
    });
  }

  closeHistory(): void {
    this.historyOpen = false;
    this.historyKey = null;
    this.history = [];
  }

  // ========== HELPERS ==========
  humanLabel(key: string): string {
    const last = key.split('.').pop() || key;
    return last.replace(/_/g, ' ')
               .replace(/\b\w/g, ch => ch.toUpperCase());
  }

  private describeError(err: HttpErrorResponse): string {
    const body = err.error;
    if (body && typeof body === 'object') {
      if (body.message) return String(body.message);
      if (body.error) return String(body.error);
    }
    return err.statusText || `HTTP ${err.status}`;
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
