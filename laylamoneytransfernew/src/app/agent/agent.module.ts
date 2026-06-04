import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../shared/shared.module';
import { AgentLayoutComponent } from './layout/agent-layout.component';
import { AgentDashboardPage } from './dashboard/agent-dashboard.page';
import { AgentSendMoneyPage } from './send-money/agent-send-money.page';
import { AgentCommissionsPage } from './commissions/agent-commissions.page';

const routes: Routes = [
  {
    path: '',
    component: AgentLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: AgentDashboardPage },
      { path: 'send', component: AgentSendMoneyPage },
      { path: 'commissions', component: AgentCommissionsPage }
    ]
  }
];

@NgModule({
  declarations: [
    AgentLayoutComponent,
    AgentDashboardPage,
    AgentSendMoneyPage,
    AgentCommissionsPage
  ],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class AgentModule {}
