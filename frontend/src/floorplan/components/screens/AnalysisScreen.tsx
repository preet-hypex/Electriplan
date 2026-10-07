import type { FloorPlan } from '../../model/types'

/**
 * Reports what the backend actually did. Every number here comes from the
 * analysis response — nothing is hard-coded.
 */
export function AnalysisScreen({
  state,
  plan,
  error,
  onOpen,
  onRetry,
  onManual,
  onBack,
}: {
  state: 'running' | 'done' | 'error'
  plan: FloorPlan | null
  error: string | null
  onOpen: () => void
  onRetry: () => void
  onManual: () => void
  onBack: () => void
}) {
  const report = plan?.analysis

  return (
    <div className="flex h-full w-full items-center justify-center bg-slate-100 p-8">
      <div className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-slate-900">
          {state === 'running' ? 'Analysing floor plan…' : state === 'error' ? 'Analysis failed' : 'Analysis complete'}
        </h1>

        {state === 'running' && (
          <div className="mt-4 space-y-2 text-sm text-slate-600">
            <p>Running the pipeline: preprocess → region → walls → rooms → OCR → scale.</p>
            <div className="h-1 w-full overflow-hidden rounded bg-slate-200">
              <div className="h-full w-1/4 animate-indeterminate rounded bg-blue-500 motion-reduce:w-full motion-reduce:animate-pulse" />
            </div>
          </div>
        )}

        {state === 'error' && (
          <div className="mt-4 space-y-4">
            <p className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={onRetry}
                className="rounded bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700"
              >
                Try again
              </button>
              <button
                type="button"
                onClick={onManual}
                className="rounded border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
              >
                Manually trace walls
              </button>
              <button
                type="button"
                onClick={onBack}
                className="rounded px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-100"
              >
                Back
              </button>
            </div>
          </div>
        )}

        {state === 'done' && report && (
          <div className="mt-4 space-y-4">
            <ul className="space-y-1 text-sm">
              {report.steps.map((s) => (
                <li key={s.name} className="flex items-baseline gap-2">
                  <span className={s.ok ? 'text-green-600' : 'text-amber-600'}>{s.ok ? '✓' : '!'}</span>
                  <span className="text-slate-800">{s.name}</span>
                  <span className="ml-auto text-xs text-slate-400">{s.detail}</span>
                </li>
              ))}
            </ul>

            <dl className="grid grid-cols-4 gap-2 rounded border border-slate-200 bg-slate-50 p-3 text-center">
              <Stat label="walls" value={report.wallCount} />
              <Stat label="rooms" value={report.roomCount} />
              <Stat label="labels" value={report.labelCount} />
              <Stat label="dimensions" value={report.dimensionCount} />
            </dl>

            {report.warnings.length > 0 && (
              <ul className="space-y-1 rounded border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
                {report.warnings.map((w) => (
                  <li key={w}>• {w}</li>
                ))}
              </ul>
            )}

            <div className="flex gap-2">
              <button
                type="button"
                onClick={onOpen}
                className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
              >
                Open in Editor
              </button>
              <button
                type="button"
                onClick={onRetry}
                className="rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100"
              >
                Try again
              </button>
              <button
                type="button"
                onClick={onBack}
                className="rounded px-3 py-2 text-sm text-slate-500 hover:bg-slate-100"
              >
                Upload a different image
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dd className="text-xl font-semibold tabular-nums text-slate-900">{value}</dd>
      <dt className="text-[11px] uppercase tracking-wide text-slate-500">{label}</dt>
    </div>
  )
}
