'use client';
import { useEffect, useState } from 'react';
import LeakGraph from '../components/LeakGraph';

export default function Home() {
  const [reports, setReports] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/reports')
      .then((res) => res.json())
      .then((data) => {
        setReports(data);
        setLoading(false);
      });
  }, []);

  if (loading) return <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">Loading JPoint Telemetry...</div>;

  return (
    <main className="min-h-screen bg-gray-950 text-gray-200 p-10 font-mono">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold text-emerald-400 mb-8 border-b border-gray-800 pb-4">
          JPoint Observer Deck
        </h1>
        
        {reports.length === 0 ? (
          <p className="text-gray-500">No OOM reports detected in MinIO.</p>
        ) : (
          <div className="grid grid-cols-1 gap-6">
            {reports.map((report, idx) => (
              <div key={idx} className="bg-gray-900 border border-gray-800 rounded-lg p-6 shadow-xl">
                <div className="flex justify-between items-center mb-4">
                  <h2 className="text-xl font-bold text-blue-400 truncate">Trace ID: {report.traceId}</h2>
                  <span className="bg-emerald-900 text-emerald-300 px-3 py-1 rounded-full text-xs border border-emerald-700">
                    {report.status}
                  </span>
                </div>
                
                <div className="text-sm text-gray-400 mb-6 border-b border-gray-800 pb-4">
                  <p>Job ID: {report.jobId}</p>
                  <p>Target File: {report.targetFile}</p>
                </div>

                <div>
                  <h3 className="text-lg text-rose-400 mb-3 font-semibold border-b border-gray-800 pb-2">Leak Suspects Found</h3>
                  
                  <div className="mb-6">
                    <LeakGraph suspects={report.leakSuspects} />
                  </div>

                  {report.leakSuspects.map((suspect: any, i: number) => (
                    <div key={i} className="mb-4 bg-gray-950 p-4 rounded border border-gray-800">
                      
                      {suspect.gitBlame && (
                        <div className="mb-4 flex items-center gap-4 bg-gray-900 p-3 rounded-lg border border-gray-700">
                          {suspect.gitBlame.avatarUrl && (
                            <img src={suspect.gitBlame.avatarUrl} alt="Avatar" className="w-10 h-10 rounded-full border border-gray-600" />
                          )}
                          <div>
                            <p className="text-sm text-gray-200 font-semibold">
                              Culprit: <span className="text-rose-400">{suspect.gitBlame.username}</span> ({suspect.gitBlame.author})
                            </p>
                            <p className="text-xs text-gray-400 font-mono">
                              Commit: {suspect.gitBlame.commitHash} • File: {suspect.gitBlame.filePath}
                            </p>
                          </div>
                        </div>
                      )}
                      
                      <div className="flex flex-wrap gap-2 mb-3">
                        {suspect.suspectClasses.map((cls: string, j: number) => (
                          <span key={j} className="bg-rose-950 text-rose-300 px-2 py-1 rounded text-xs border border-rose-800">
                            {cls}
                          </span>
                        ))}
                      </div>
                      <p className="text-sm text-gray-300 leading-relaxed whitespace-pre-wrap">{suspect.description}</p>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}