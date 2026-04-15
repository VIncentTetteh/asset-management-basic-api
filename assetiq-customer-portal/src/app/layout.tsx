import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "AssetIQ — Standalone Licensing",
  description: "Purchase and manage your AssetIQ on-premise license",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="bg-slate-50 text-slate-900 min-h-screen">
        {/* Nav */}
        <header className="border-b border-slate-200 bg-white">
          <div className="mx-auto max-w-6xl px-6 py-4 flex items-center justify-between">
            <a href="/" className="flex items-center gap-2">
              <div className="h-8 w-8 rounded-lg bg-brand-600 flex items-center justify-center">
                <span className="text-white font-bold text-sm">A</span>
              </div>
              <span className="font-bold text-slate-900">AssetIQ</span>
              <span className="text-xs bg-slate-100 text-slate-500 rounded px-1.5 py-0.5 ml-1 font-medium">
                Standalone
              </span>
            </a>
            <nav className="flex items-center gap-6 text-sm">
              <a href="/" className="text-slate-600 hover:text-slate-900 transition-colors">Pricing</a>
              <a href="/dashboard" className="text-slate-600 hover:text-slate-900 transition-colors">My License</a>
              <a href="https://docs.assetiq.io" target="_blank" rel="noopener noreferrer"
                 className="text-slate-600 hover:text-slate-900 transition-colors">Docs</a>
            </nav>
          </div>
        </header>

        <main>{children}</main>

        {/* Footer */}
        <footer className="border-t border-slate-200 mt-24 py-10">
          <div className="mx-auto max-w-6xl px-6 flex flex-col sm:flex-row items-center justify-between gap-4">
            <p className="text-sm text-slate-500">
              © {new Date().getFullYear()} AssetIQ. All rights reserved.
            </p>
            <div className="flex gap-6 text-sm text-slate-500">
              <a href="mailto:support@assetiq.io" className="hover:text-slate-900 transition-colors">Support</a>
              <a href="https://docs.assetiq.io" target="_blank" rel="noopener noreferrer"
                 className="hover:text-slate-900 transition-colors">Documentation</a>
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
