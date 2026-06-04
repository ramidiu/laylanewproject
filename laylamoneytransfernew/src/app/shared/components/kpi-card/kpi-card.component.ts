import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-kpi-card',
  template: `
    <div class="fb-kpi-card" [ngClass]="variantClass">
      <div class="kpi-icon" *ngIf="icon">
        <ion-icon [name]="icon" color="primary"></ion-icon>
      </div>
      <div class="kpi-value">{{ value }}</div>
      <div class="kpi-label">{{ label }}</div>
      <div class="kpi-trend" *ngIf="trend" [ngClass]="trendDirection">
        <ion-icon [name]="trendDirection === 'up' ? 'trending-up' : 'trending-down'"></ion-icon>
        {{ trend }}
      </div>
    </div>
  `,
  styleUrls: ['./kpi-card.component.scss']
})
export class KpiCardComponent {
  @Input() value: string = '';
  @Input() label: string = '';
  @Input() trend: string = '';
  @Input() trendDirection: 'up' | 'down' = 'up';
  @Input() variant: 'default' | 'sky' | 'success' | 'warning' = 'default';
  @Input() icon: string = '';

  get variantClass(): string {
    return this.variant !== 'default' ? `fb-kpi-card--${this.variant}` : '';
  }
}
