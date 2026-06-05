import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { IonicModule } from '@ionic/angular';
import { ReceiptViewPage } from './receipt-view.page';

const routes: Routes = [{ path: '', component: ReceiptViewPage }];

@NgModule({
  imports: [CommonModule, IonicModule, RouterModule.forChild(routes)],
  declarations: [ReceiptViewPage]
})
export class ReceiptViewModule {}
