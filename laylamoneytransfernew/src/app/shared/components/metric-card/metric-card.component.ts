import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-metric-card',
  template: `
    <div class="metric-card" [class.metric-card--clickable]="clickable">
      <div class="metric-card__icon" [style.background]="iconBg">
        <ion-icon [name]="icon"></ion-icon>
      </div>
      <div class="metric-card__body">
        <div class="metric-card__label">{{ label }}</div>
        <div class="metric-card__value">{{ value }}</div>
        <div class="metric-card__change" *ngIf="change !== undefined"
             [class.metric-card__change--up]="change >= 0"
             [class.metric-card__change--down]="change < 0">
          <ion-icon [name]="change >= 0 ? 'arrow-up' : 'arrow-down'"></ion-icon>
          {{ change >= 0 ? '+' : '' }}{{ change | number:'1.1-1' }}%
          <span class="metric-card__period" *ngIf="changePeriod">vs {{ changePeriod }}</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .metric-card {
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: 12px;
      padding: 20px;
      display: flex;
      align-items: flex-start;
      gap: 16px;
      transition: box-shadow 0.2s;

      &--clickable { cursor: pointer; &:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); } }

      &__icon {
        width: 44px; height: 44px; border-radius: 10px;
        display: flex; align-items: center; justify-content: center;
        color: #fff; font-size: 22px; flex-shrink: 0;
      }

      &__label { font-size: 12px; color: #6b7280; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 4px; }
      &__value { font-size: 26px; font-weight: 700; color: #111827; line-height: 1.2; font-variant-numeric: tabular-nums; }
      &__change {
        display: inline-flex; align-items: center; gap: 2px;
        font-size: 12px; font-weight: 600; margin-top: 4px;
        ion-icon { font-size: 12px; }
        &--up { color: #059669; }
        &--down { color: #dc2626; }
      }
      &__period { color: #9ca3af; font-weight: 400; margin-left: 4px; }
    }
  `]
})
export class MetricCardComponent {
  @Input() label = '';
  @Input() value: string | number = '—';
  @Input() icon = 'analytics-outline';
  @Input() iconBg = '#1B3571';
  @Input() change?: number;
  @Input() changePeriod?: string;
  @Input() clickable = false;
}
