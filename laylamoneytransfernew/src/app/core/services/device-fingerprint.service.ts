import { Injectable } from '@angular/core';
import FingerprintJS, { Agent, GetResult } from '@fingerprintjs/fingerprintjs';

/**
 * Computes a stable browser/device fingerprint using FingerprintJS open-source.
 * The resulting visitorId is cached in memory + localStorage and sent as
 * X-Visitor-Id on every HTTP request via the auth interceptor.
 */
@Injectable({
  providedIn: 'root'
})
export class DeviceFingerprintService {
  private static readonly STORAGE_KEY = 'fb_visitor_id';

  private agentPromise: Promise<Agent> | null = null;
  private cachedVisitorId: string | null = null;

  constructor() {
    const stored = this.readStored();
    if (stored) {
      this.cachedVisitorId = stored;
    }
    // Pre-warm the fingerprint computation on app startup so the first
    // request already has X-Visitor-Id attached.
    void this.resolveVisitorId();
  }

  getVisitorIdSync(): string | null {
    return this.cachedVisitorId;
  }

  async resolveVisitorId(): Promise<string | null> {
    if (this.cachedVisitorId) return this.cachedVisitorId;
    try {
      const agent = await this.loadAgent();
      const result: GetResult = await agent.get();
      this.cachedVisitorId = result.visitorId;
      this.persist(result.visitorId);
      return result.visitorId;
    } catch (err) {
      console.warn('FingerprintJS failed to compute visitor id:', err);
      return null;
    }
  }

  private loadAgent(): Promise<Agent> {
    if (!this.agentPromise) {
      this.agentPromise = FingerprintJS.load();
    }
    return this.agentPromise;
  }

  private persist(id: string): void {
    try {
      localStorage.setItem(DeviceFingerprintService.STORAGE_KEY, id);
    } catch {}
  }

  private readStored(): string | null {
    try {
      return localStorage.getItem(DeviceFingerprintService.STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
