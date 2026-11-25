import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { ReconcileRequest, Report } from './report';

/** Calls the Spring Boot backend, served on the same origin through the dev proxy. */
@Service()
export class ReconcileApi {
  private readonly http = inject(HttpClient);

  compare(request: ReconcileRequest): Observable<Report> {
    return this.http.post<Report>('/api/reconcile', toForm(request));
  }

  csv(request: ReconcileRequest): Observable<Blob> {
    return this.http.post('/api/reconcile/csv', toForm(request), { responseType: 'blob' });
  }
}

function toForm(request: ReconcileRequest): FormData {
  const form = new FormData();
  form.append('oldFec', request.oldFec);
  form.append('newFec', request.newFec);
  if (request.mapping) {
    form.append('mapping', request.mapping);
  }
  form.append('mode', request.mode);
  form.append('openingJournals', request.openingJournals);
  form.append('balanceSheetOnly', String(request.balanceSheetOnly));
  return form;
}
