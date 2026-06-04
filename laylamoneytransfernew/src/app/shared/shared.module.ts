import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { RouterModule } from '@angular/router';
import { NgChartsModule } from 'ng2-charts';
import { TranslateModule } from '@ngx-translate/core';
import { StatusChipComponent } from './components/status-chip/status-chip.component';
import { KpiCardComponent } from './components/kpi-card/kpi-card.component';
import { NavbarComponent } from './components/navbar/navbar.component';
import { FxCalculatorComponent } from './components/fx-calculator/fx-calculator.component';
import { MetricCardComponent } from './components/metric-card/metric-card.component';
import { ChartPanelComponent } from './components/chart-panel/chart-panel.component';
import { CurrencyFormatPipe } from './pipes/currency-format.pipe';
import { CountryFlagUrlPipe, CountryFlagSvgPipe } from './pipes/country-flag.pipe';
import { AddressAutocompleteComponent } from './components/address-autocomplete/address-autocomplete.component';

@NgModule({
  declarations: [
    StatusChipComponent,
    KpiCardComponent,
    NavbarComponent,
    FxCalculatorComponent,
    MetricCardComponent,
    ChartPanelComponent,
    CurrencyFormatPipe,
    CountryFlagUrlPipe,
    CountryFlagSvgPipe,
    AddressAutocompleteComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule,
    RouterModule,
    NgChartsModule,
    TranslateModule
  ],
  exports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule,
    RouterModule,
    NgChartsModule,
    TranslateModule,
    StatusChipComponent,
    KpiCardComponent,
    NavbarComponent,
    FxCalculatorComponent,
    MetricCardComponent,
    ChartPanelComponent,
    CurrencyFormatPipe,
    CountryFlagUrlPipe,
    CountryFlagSvgPipe,
    AddressAutocompleteComponent
  ]
})
export class SharedModule {}
