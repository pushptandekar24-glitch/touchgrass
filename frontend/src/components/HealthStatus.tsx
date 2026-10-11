import React, { useEffect, useState } from 'react';
import { fetchHealth } from '../api/client';
import { HealthInfo } from '../types';
import { Activity, CheckCircle2, AlertTriangle, RefreshCw } from 'lucide-react';

export const HealthStatus: React.FC = () => {
  const [health, setHealth] = useState<HealthInfo | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const checkHealth = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetchHealth();
      setHealth(response.data);
    } catch (err: any) {
      setError(err.message || 'Could not connect to Spring Boot backend API');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkHealth();
  }, []);

  return (
    <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-sm">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <Activity className="w-5 h-5 text-emerald-400" />
          <h3 className="font-semibold text-slate-100">Backend API System Status</h3>
        </div>
        <button
          onClick={checkHealth}
          disabled={loading}
          className="p-2 text-slate-400 hover:text-emerald-400 transition-colors rounded-lg hover:bg-slate-800 disabled:opacity-50"
          title="Refresh Backend Status"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {loading && (
        <div className="py-4 text-center text-sm text-slate-400 animate-pulse">
          Connecting to Spring Boot REST API at http://localhost:8080/api/health...
        </div>
      )}

      {!loading && error && (
        <div className="bg-rose-950/40 border border-rose-800/50 rounded-xl p-4 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-rose-200">Backend Connection Error</p>
            <p className="text-xs text-rose-300/80 mt-1">{error}</p>
            <p className="text-xs text-slate-400 mt-2">
              Run <code className="bg-slate-950 px-1.5 py-0.5 rounded text-emerald-300">mvn spring-boot:run</code> in the <code className="text-slate-300">backend/</code> directory to start the server.
            </p>
          </div>
        </div>
      )}

      {!loading && health && (
        <div className="bg-emerald-950/30 border border-emerald-800/40 rounded-xl p-4 flex items-center gap-4">
          <CheckCircle2 className="w-6 h-6 text-emerald-400 shrink-0" />
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                {health.status}
              </span>
              <span className="text-sm font-semibold text-slate-200">{health.application}</span>
            </div>
            <p className="text-xs text-slate-400">{health.phase} · Spring Boot API connected cleanly</p>
          </div>
        </div>
      )}
    </div>
  );
};
