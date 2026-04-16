#!/usr/bin/env node
/**
 * F-3: Permission type generator
 *
 * Reads the canonical Permission.java enum and emits a TypeScript union type
 * to both frontend projects.  Run this script whenever a new permission is
 * added to the backend enum:
 *
 *   node scripts/generate-permissions.mjs
 *
 * Or via the npm convenience alias (add to backend package.json if desired):
 *
 *   npm run generate:permissions
 *
 * Output files:
 *   ../Enterprise-Asset-manager-Frontend/src/types/permissions.ts
 *   ../Enterprise-Asset-manager-desktop-app/shared/permissions.ts
 */

import { readFileSync, writeFileSync, mkdirSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Source ───────────────────────────────────────────────────────────────────

const ENUM_FILE = resolve(
  __dirname,
  "../src/main/java/com/assetiq/enums/Permission.java"
);

function parsePermissions(javaSource) {
  // Strip comments and extract top-level enum constants.
  // Match identifiers that appear at the start of a line (after whitespace),
  // are ALL_CAPS_WITH_UNDERSCORES, and are followed by , or ;
  const clean = javaSource
    .replace(/\/\*[\s\S]*?\*\//g, "")   // block comments
    .replace(/\/\/.*/g, "");             // line comments

  const matches = [...clean.matchAll(/^\s+([A-Z][A-Z0-9_]+)\s*[,;]/gm)];
  return matches
    .map((m) => m[1])
    .filter((name) => name !== "Permission"); // exclude the enum declaration itself if captured
}

// ── Template ─────────────────────────────────────────────────────────────────

function renderOutput(permissions) {
  const lines = permissions.map((p, i) =>
    i === 0 ? `  | "${p}"` : `  | "${p}"`
  );

  return `// ─────────────────────────────────────────────────────────────────────────────
// AUTO-GENERATED — do not edit by hand.
// Source of truth: Enterprise-Asset-Manager/src/main/java/com/assetiq/enums/Permission.java
// Regenerate: node Enterprise-Asset-Manager/scripts/generate-permissions.mjs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Union type of every permission string recognised by the backend.
 * Consumers should use this type instead of \`string\` for permission props
 * and checks so that typos are caught at compile time.
 */
export type Permission =
${lines.join("\n")};

/**
 * Compile-time exhaustive array of all permissions.
 * Useful for permission pickers, admin UIs, and test fixtures.
 */
export const ALL_PERMISSIONS: Permission[] = [
  ${permissions.map((p) => `"${p}"`).join(",\n  ")},
];
`;
}

// ── Targets ───────────────────────────────────────────────────────────────────

const TARGETS = [
  resolve(
    __dirname,
    "../../Enterprise-Asset-manager-Frontend/src/types/permissions.ts"
  ),
  resolve(
    __dirname,
    "../../Enterprise-Asset-manager-desktop-app/shared/permissions.ts"
  ),
];

// ── Main ─────────────────────────────────────────────────────────────────────

const source = readFileSync(ENUM_FILE, "utf8");
const permissions = parsePermissions(source);

if (permissions.length === 0) {
  console.error("❌  No permissions parsed from", ENUM_FILE);
  process.exit(1);
}

console.log(`✅  Parsed ${permissions.length} permissions from Permission.java`);

const output = renderOutput(permissions);

for (const target of TARGETS) {
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, output, "utf8");
  console.log(`📝  Written → ${target}`);
}

console.log("\n🎉  Done. Commit the generated files alongside Permission.java changes.");
