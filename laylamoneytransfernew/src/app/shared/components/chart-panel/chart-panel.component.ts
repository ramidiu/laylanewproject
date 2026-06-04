import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-chart-panel',
  template: `
    <div class="chart-panel">
      <div class="chart-panel__header" *ngIf="title">
        <h3 class="chart-panel__title">{{ title }}</h3>
        <span class="chart-panel__subtitle" *ngIf="subtitle">{{ subtitle }}</span>
      </div>
      <div class="chart-panel__body" [style.height]="height">
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [`
    .chart-panel {
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: 12px;
      overflow: hidden;

      &__header {
        padding: 16px 20px 0;
        display: flex;
        align-items: baseline;
        gap: 8px;
      }
      &__title { margin: 0; font-size: 15px; font-weight: 600; color: #111827; }
      &__subtitle { font-size: 12px; color: #9ca3af; }
      &__body { padding: 12px 16px 16px; position: relative; }
    }
  `]
})
export class ChartPanelComponent {
  @Input() title = '';
  @Input() subtitle = '';
  @Input() height = '280px';
}
