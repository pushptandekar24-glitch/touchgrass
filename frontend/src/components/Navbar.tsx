import React from 'react';
import { Compass, Sparkles } from 'lucide-react';

export const Navbar: React.FC = () => {
  return (
    <header className="border-b border-emerald-900/50 bg-slate-950/80 backdrop-blur-md sticky top-0 z-50">
      <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400">
            <Compass className="w-6 h-6 animate-pulse" />
          </div>
          <div>
            <span className="font-bold text-lg text-emerald-100 flex items-center">
              WildStep
            </span>
            <p className="text-xs text-emerald-400/70">AI that gets you to close the app.</p>
          </div>
        </div>

        <div className="flex items-center gap-2 text-xs text-slate-400 bg-slate-900 border border-slate-800 px-3 py-1.5 rounded-lg">
          <Sparkles className="w-4 h-4 text-emerald-400" />
          <span>Hacktoberfest 2026</span>
        </div>
      </div>
    </header>
  );
};
