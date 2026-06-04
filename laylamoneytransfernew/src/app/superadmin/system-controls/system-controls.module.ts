import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { RouterModule } from '@angular/router';
import { SystemControlsPage } from './system-controls.page';

@NgModule({
  declarations: [SystemControlsPage],
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    RouterModule.forChild([{ path: '', component: SystemControlsPage }])
  ]
})
export class SystemControlsPageModule {}
