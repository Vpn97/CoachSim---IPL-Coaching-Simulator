import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface MatchSummary {
  id: number;
  season: string;
  homeTeam: string;
  awayTeam: string;
  venue?: string;
  status: 'SCHEDULED' | 'LIVE' | 'COMPLETED';
  source: 'MOCK' | 'EXTERNAL';
  startsAt?: string;
}

export interface BallView {
  over: number;
  ballInOver: number;
  bowler: string;
  batter: string;
  runs: number;
  extras: number;
  wicket: boolean;
  phase: string | null;
}

export interface InningsState {
  number: number;
  battingTeam: string;
  bowlingTeam: string;
  totalRuns: number;
  wickets: number;
  legalBalls: number;
  lastBall: BallView | null;
}

export interface LiveState {
  match: MatchSummary;
  innings: InningsState[];
}

export interface WindowView {
  id: number;
  matchId: number;
  type: 'BOWLING_CHANGE' | 'FIELD_SET';
  over: number;
  ball: number;
  opensAt: string;
  closesAt: string;
  status: 'OPEN' | 'CLOSED' | 'RESOLVED';
}

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);

  liveMatches(): Observable<MatchSummary[]> {
    return this.http.get<MatchSummary[]>('/api/matches/live');
  }

  allMatches(): Observable<MatchSummary[]> {
    return this.http.get<MatchSummary[]>('/api/matches');
  }

  matchState(id: number): Observable<LiveState> {
    return this.http.get<LiveState>(`/api/matches/${id}/state`);
  }

  openWindows(matchId: number): Observable<WindowView[]> {
    return this.http.get<WindowView[]>(`/api/decisions/windows/open?matchId=${matchId}`);
  }

  submitDecision(windowId: number, payload: Record<string, unknown>): Observable<unknown> {
    return this.http.post('/api/decisions', { windowId, payload });
  }

  myHistory(): Observable<unknown[]> {
    return this.http.get<unknown[]>('/api/decisions/history');
  }

  /** Running tactical-merit snapshot for the current user in a single match. */
  myScore(matchId: number): Observable<MyScore> {
    return this.http.get<MyScore>(`/api/decisions/my-score?matchId=${matchId}`);
  }
}

export interface ScoreBreakdown {
  totalPoints: number;
  maxPoints: number;
  normalised: number;
  rules: { rule: string; points: number; maxPoints: number; detail: string }[];
}

export interface MyScore {
  matchId: number;
  totalScore: number;
  decisionsScored: number;
  decisionsPending: number;
  averageScore: number;
  bestScore: number;
  lastScore: number | null;
  lastBreakdown: ScoreBreakdown | null;
  lastScoredAt: string | null;
}
