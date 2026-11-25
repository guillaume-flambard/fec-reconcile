import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { Line, STATUS_LABELS } from './report';

/** Balances of one account or third party in both systems, differences first. */
@Component({
  selector: 'app-balance-table',
  imports: [DecimalPipe],
  template: `
    @if (lines().length === 0) {
      <p class="none">Aucun écart.</p>
    } @else {
      <div class="table-wrap" tabindex="0" [attr.aria-label]="caption()">
        <table>
          <caption class="visually-hidden">{{ caption() }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ keyLabel() }}</th>
              <th scope="col">Libellé</th>
              @if (showOldAccounts()) {
                <th scope="col">Ancien(s) compte(s)</th>
              }
              <th scope="col" class="num">Solde ancien</th>
              <th scope="col" class="num">Solde nouveau</th>
              <th scope="col" class="num">Écart</th>
              <th scope="col">Statut</th>
            </tr>
          </thead>
          <tbody>
            @for (l of lines(); track l.key) {
              <tr [class.diff]="l.status !== 'OK'">
                <td class="key">{{ l.key }}</td>
                <td>{{ l.label }}</td>
                @if (showOldAccounts()) {
                  <td>{{ l.oldAccounts.join(' + ') }}</td>
                }
                <td class="num">{{ l.oldBalance | number: '1.2-2' }}</td>
                <td class="num">{{ l.newBalance | number: '1.2-2' }}</td>
                <td class="num">{{ l.difference | number: '1.2-2' }}</td>
                <td>
                  <span class="badge" [class.badge-ok]="l.status === 'OK'">{{ labels[l.status] }}</span>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class BalanceTable {
  readonly lines = input.required<Line[]>();
  readonly keyLabel = input.required<string>();
  readonly caption = input.required<string>();
  readonly showOldAccounts = input(false);
  protected readonly labels = STATUS_LABELS;
}
