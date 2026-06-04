import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpResponse
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

/**
 * Auto-unwraps the standard ApiResponse<T> wrapper ({success, data, message})
 * returned by all Spring Boot services.
 *
 * Before this interceptor, every component had to manually read `res.data` or
 * `res?.data || res`, and many read `res.content` expecting the Spring Data
 * Page structure that lives INSIDE `data`. This caused blank pages everywhere
 * the pattern was missed.
 *
 * After: the response body seen by subscribers is already `data` (the inner
 * payload), so `res.content` on a paginated endpoint just works — it reads
 * from the Page object directly.
 *
 * Skips non-JSON responses (file downloads, health checks, etc.).
 */
@Injectable()
export class ApiResponseUnwrapInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      map(event => {
        if (event instanceof HttpResponse && event.body != null) {
          const body = event.body;
          // Only unwrap if it looks like our ApiResponse wrapper:
          // {success: boolean, data: <anything>, message?: string}
          if (typeof body === 'object' && 'success' in body && 'data' in body) {
            return event.clone({ body: body.data });
          }
        }
        return event;
      })
    );
  }
}
