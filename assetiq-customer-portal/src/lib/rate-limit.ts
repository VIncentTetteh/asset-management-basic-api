/**
 * In-process sliding-window rate limiter for Next.js API routes.
 *
 * Uses a Map stored in the module scope (survives across requests within the
 * same serverless function instance / Node process).  For multi-instance
 * deployments swap the store for Redis using the same interface.
 *
 * Usage:
 *   const limit = rateLimit({ windowMs: 60_000, max: 5 });
 *   const { success } = limit.check(ip);
 *   if (!success) return NextResponse.json({ error: 'Too many requests' }, { status: 429 });
 */

interface Entry { count: number; resetAt: number }

export function rateLimit(options: { windowMs: number; max: number }) {
  const store = new Map<string, Entry>();

  // Prune stale entries periodically so the Map doesn't grow unbounded
  const prune = () => {
    const now = Date.now();
    for (const [key, entry] of store) {
      if (entry.resetAt < now) store.delete(key);
    }
  };

  return {
    check(key: string): { success: boolean; remaining: number; resetAt: number } {
      prune();
      const now = Date.now();
      const entry = store.get(key);

      if (!entry || entry.resetAt < now) {
        // First request in this window
        const resetAt = now + options.windowMs;
        store.set(key, { count: 1, resetAt });
        return { success: true, remaining: options.max - 1, resetAt };
      }

      if (entry.count >= options.max) {
        return { success: false, remaining: 0, resetAt: entry.resetAt };
      }

      entry.count += 1;
      return { success: true, remaining: options.max - entry.count, resetAt: entry.resetAt };
    },
  };
}

/** Shared limiters — one per sensitive route */
export const checkoutLimiter = rateLimit({ windowMs: 60_000, max: 5 });   // 5 checkouts / IP / min
export const verifyLimiter   = rateLimit({ windowMs: 60_000, max: 10 });  // 10 verifies / IP / min
export const lookupLimiter   = rateLimit({ windowMs: 60_000, max: 20 });  // 20 lookups  / IP / min
