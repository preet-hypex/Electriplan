import { useCallback, useRef, useState } from 'react'

const ACCEPTED = ['image/jpeg', 'image/png']
/** The analyser refuses anything larger; say so before uploading it. */
const MAX_BYTES = 25 * 1024 * 1024

/** What the analysis finds, so the first screen says what to expect. */
const FINDS = [
  { title: 'Walls and openings', text: 'Walls with their thickness, plus doors, windows and gaps.' },
  { title: 'Rooms and names', text: 'Each room as a shape, named from the labels on the plan.' },
  { title: 'Scale', text: 'Millimetres per pixel, read from the printed dimensions.' },
]

export function UploadScreen({
  onFile,
  onUseSample,
  onOpenJson,
  apiReachable,
}: {
  onFile: (file: File) => void
  onUseSample: () => void
  /** Reopen a plan saved earlier with "Save JSON". Rejects when the file is not a plan. */
  onOpenJson: (file: File) => Promise<void>
  apiReachable: boolean | null
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const jsonRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [preview, setPreview] = useState<{ url: string; name: string; file: File } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const accept = useCallback((file: File | undefined) => {
    if (!file) return
    if (!ACCEPTED.includes(file.type)) {
      setError(`${file.name} is a ${file.type || 'file of unknown type'}. Upload a JPG or PNG.`)
      return
    }
    if (file.size > MAX_BYTES) {
      setError(`${file.name} is ${(file.size / 1e6).toFixed(1)} MB. The limit is 25 MB.`)
      return
    }
    setError(null)
    setPreview({ url: URL.createObjectURL(file), name: file.name, file })
  }, [])

  const primary =
    'inline-flex h-10 items-center gap-2 rounded bg-blue-600 px-5 text-sm font-medium text-white hover:bg-blue-700'
  const secondary =
    'inline-flex h-9 items-center rounded border border-slate-200 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50'

  return (
    <div className="h-full w-full overflow-auto bg-slate-100">
      <div className="mx-auto flex max-w-3xl flex-col gap-6 px-6 py-10">
        <header>
          <p className="text-sm font-semibold text-blue-600">New floor plan</p>
          <h2 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">
            Upload a floor-plan image
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-slate-600">
            We find the walls, rooms, doors and windows, read the room names and work out the scale.
            You then check and correct the plan in the editor. Your image is never changed — it stays
            behind the plan as a reference.
          </p>
        </header>

        {apiReachable === false && (
          <p className="rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            The floor-plan analyser is not answering at <code>/api/floorplan</code>. Start it with{' '}
            <code>docker compose up -d floorplan</code> (or <code>floorplan/run.sh</code>), or open the
            sample plan below.
          </p>
        )}

        <div
          onDragOver={(e) => {
            e.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault()
            setDragging(false)
            accept(e.dataTransfer.files?.[0])
          }}
          className={[
            'flex flex-col items-center justify-center rounded-lg border-2 border-dashed px-8 py-12 text-center transition',
            dragging ? 'border-blue-500 bg-blue-50' : 'border-slate-300 bg-white',
          ].join(' ')}
        >
          {preview ? (
            <div className="flex w-full flex-col items-center gap-4">
              <img
                src={preview.url}
                alt={`Preview of ${preview.name}`}
                className="max-h-72 w-auto rounded border border-slate-200 object-contain"
              />
              <p className="text-xs text-slate-500">{preview.name}</p>
              <div className="flex gap-2">
                <button type="button" className={primary} onClick={() => onFile(preview.file)}>
                  Analyse this plan
                </button>
                <button type="button" className={secondary + ' h-10'} onClick={() => setPreview(null)}>
                  Choose another
                </button>
              </div>
            </div>
          ) : (
            <>
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-blue-100 text-blue-600">
                <svg width="22" height="22" viewBox="0 0 20 20" fill="none" stroke="currentColor"
                  strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M10 13V3.5m0 0L6.5 7M10 3.5 13.5 7M3.5 12.5v3a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1v-3" />
                </svg>
              </span>
              <p className="mt-4 text-base font-semibold text-slate-900">Drop your floor-plan image here</p>
              <p className="mt-1 text-sm text-slate-500">JPG or PNG, up to 25 MB</p>
              <button type="button" className={primary + ' mt-5'} onClick={() => inputRef.current?.click()}>
                Choose a file
              </button>
              <input
                ref={inputRef}
                type="file"
                accept="image/jpeg,image/png"
                className="hidden"
                onChange={(e) => {
                  accept(e.target.files?.[0])
                  e.target.value = ''
                }}
              />
            </>
          )}
        </div>

        {error && (
          <p className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>
        )}

        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {FINDS.map((f) => (
            <li key={f.title} className="rounded-lg border border-slate-200 bg-white p-4">
              <p className="text-sm font-semibold text-slate-900">{f.title}</p>
              <p className="mt-1 text-xs leading-relaxed text-slate-500">{f.text}</p>
            </li>
          ))}
        </ul>

        <div className="flex flex-wrap items-center gap-3 border-t border-slate-200 pt-5">
          <span className="text-sm text-slate-500">No image to hand?</span>
          <button type="button" className={secondary} onClick={onUseSample}>
            Try the sample plan
          </button>
          <button type="button" className={secondary} onClick={() => jsonRef.current?.click()}>
            Open a saved plan (JSON)
          </button>
          <input
            ref={jsonRef}
            type="file"
            accept="application/json,.json"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) {
                setError(null)
                onOpenJson(f).catch((err: Error) => setError(`${f.name} could not be opened: ${err.message}`))
              }
              e.target.value = ''
            }}
          />
        </div>
      </div>
    </div>
  )
}
