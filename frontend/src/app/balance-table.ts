import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { Line, STATUS_LABELS } from './report';

/** Balances of each account or third party in both systems, differences first. */
@Component({
  selector: 'app-balance-table',
  imports: [DecimalPipe, HlmTableImports],
  template: `
    @if (lines().length === 0) {
      <p class="text-sm text-muted-foreground">Aucune ligne dans les fichiers.</p>
    } @else {
      <div hlmTableContainer tabindex="0" [attr.aria-label]="caption()">
        <table hlmTable>
          <caption hlmTableCaption class="sr-only">{{ caption() }}</caption>
          <thead hlmTableHeader>
            <tr hlmTableRow>
              <th hlmTableHead>{{ keyLabel() }}</th>
              <th hlmTableHead>Libellé</th>
              @if (showOldAccounts()) {
                <th hlmTableHead>Ancien(s) compte(s)</th>
              }
              <th hlmTableHead class="text-right">Avant</th>
              <th hlmTableHead class="text-right">Après</th>
              <th hlmTableHead class="text-right">Écart</th>
              <th hlmTableHead>Statut</th>
            </tr>
          </thead>
          <tbody hlmTableBody>
            @for (l of lines(); track l.key) {
              <tr hlmTableRow [class.bg-destructive-soft]="l.status !== 'OK'">
                <td hlmTableCell class="font-mono">{{ l.key }}</td>
                <td hlmTableCell>{{ l.label }}</td>
                @if (showOldAccounts()) {
                  <td hlmTableCell class="font-mono">{{ l.oldAccounts.join(' + ') }}</td>
                }
                <td hlmTableCell class="text-right tabular-nums">{{ l.oldBalance | number: '1.2-2' }}</td>
                <td hlmTableCell class="text-right tabular-nums">{{ l.newBalance | number: '1.2-2' }}</td>
                <td hlmTableCell class="text-right tabular-nums">{{ l.difference | number: '1.2-2' }}</td>
                <td hlmTableCell [class]="l.status === 'OK' ? 'text-success' : 'font-medium text-destructive'">
                  {{ labels[l.status] }}
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
