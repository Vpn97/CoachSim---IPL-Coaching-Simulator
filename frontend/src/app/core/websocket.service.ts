import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private auth = inject(AuthService);
  private stomp: RxStomp | null = null;

  connect(): void {
    if (this.stomp) return;

    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const config: RxStompConfig = {
      brokerURL: `${proto}://${window.location.host}/ws`,
      connectHeaders: this.auth.token() ? { Authorization: `Bearer ${this.auth.token()}` } : {},
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 3000
    };

    this.stomp = new RxStomp();
    this.stomp.configure(config);
    this.stomp.activate();
  }

  disconnect(): void {
    this.stomp?.deactivate();
    this.stomp = null;
  }

  watchMatch<T = unknown>(matchId: number): Observable<T> {
    return this.topic<T>(`/topic/match.${matchId}`);
  }

  watchWindows<T = unknown>(matchId: number): Observable<T> {
    return this.topic<T>(`/topic/match.${matchId}.windows`);
  }

  watchMyResults<T = unknown>(): Observable<T> {
    return this.topic<T>(`/user/queue/decision-result`);
  }

  private topic<T>(destination: string): Observable<T> {
    this.connect();
    return this.stomp!.watch(destination).pipe(
      filter(m => !!m.body),
      map(m => JSON.parse(m.body) as T)
    );
  }
}
