/**
 * Unit tests — rate-limit.ts
 */

import { describe, it, expect, vi, afterEach } from "vitest";
import { rateLimit } from "@/lib/rate-limit";

describe("rateLimit()", () => {
  it("allows requests up to max", () => {
    const limiter = rateLimit({ windowMs: 10_000, max: 3 });
    expect(limiter.check("ip-a").success).toBe(true);  // 1
    expect(limiter.check("ip-a").success).toBe(true);  // 2
    expect(limiter.check("ip-a").success).toBe(true);  // 3
    expect(limiter.check("ip-a").success).toBe(false); // 4 — blocked
  });

  it("does not count different IPs against each other", () => {
    const limiter = rateLimit({ windowMs: 10_000, max: 1 });
    expect(limiter.check("ip-x").success).toBe(true);
    expect(limiter.check("ip-y").success).toBe(true); // different IP — allowed
    expect(limiter.check("ip-x").success).toBe(false); // ip-x already maxed
  });

  it("resets after the window expires", async () => {
    const limiter = rateLimit({ windowMs: 50, max: 1 });
    expect(limiter.check("ip-b").success).toBe(true);
    expect(limiter.check("ip-b").success).toBe(false); // blocked

    // Wait for window to expire
    await new Promise((r) => setTimeout(r, 60));

    expect(limiter.check("ip-b").success).toBe(true); // window reset
  });

  it("returns correct remaining count", () => {
    const limiter = rateLimit({ windowMs: 10_000, max: 5 });
    const first = limiter.check("ip-c");
    expect(first.remaining).toBe(4);
    const second = limiter.check("ip-c");
    expect(second.remaining).toBe(3);
  });
});
