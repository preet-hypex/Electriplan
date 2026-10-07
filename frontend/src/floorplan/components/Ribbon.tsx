import { useEditor, type Tool, type ViewMode } from '../state/store'
import { Icon, type IconName } from './icons'

const TOOLS: Array<{ id: Tool; label: string; icon: IconName; hint: string; shortcut: string }> = [
  { id: 'select', label: 'Select', icon: 'select', hint: 'Select and move walls, rooms and labels', shortcut: 'V' },
  { id: 'wall', label: 'Wall', icon: 'wall', hint: 'Drag to draw a wall. Shift holds it straight.', shortcut: 'W' },
  { id: 'room', label: 'Room', icon: 'room', hint: 'Click a room to select it', shortcut: 'R' },
  { id: 'door', label: 'Door', icon: 'door', hint: 'Click a wall to place a door', shortcut: 'D' },
  { id: 'window', label: 'Window', icon: 'window', hint: 'Click a wall to place a window', shortcut: 'N' },
]

const MODES: Array<{ id: ViewMode; label: string; hint: string }> = [
  { id: 'original', label: 'Original', hint: 'The uploaded image on its own' },
  { id: 'reconstruction', label: 'Plan', hint: 'The generated geometry on its own' },
  { id: 'overlay', label: 'Overlay', hint: 'Geometry drawn over a faded original' },
]

const SHORTCUTS: Array<[string, string]> = [
  ['V W R D N', 'Select, wall, room, door, window'],
  ['Space / Alt + drag', 'Pan'],
  ['Wheel', 'Zoom'],
  ['Shift + drag', 'Keep a wall straight'],
  ['T', 'Show or hide text and labels'],
  ['Delete', 'Remove the selection'],
  ['⌘Z / ⌘⇧Z', 'Undo / redo'],
]

/** A labelled cluster of commands, like a ribbon group in desktop software. */
function Group({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex shrink-0 flex-col justify-between gap-1 border-r border-slate-200 px-2 last:border-r-0">
      <div className="flex h-11 items-center gap-0.5">{children}</div>
      <span className="text-center text-[10px] font-semibold uppercase tracking-wider text-slate-400">{label}</span>
    </div>
  )
}

function ToggleButton({
  active,
  onClick,
  icon,
  label,
  title,
  disabled,
}: {
  active: boolean
  onClick: () => void
  icon?: IconName
  label: string
  title?: string
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      title={title ?? label}
      aria-label={title ?? label}
      disabled={disabled}
      onClick={onClick}
      className={[
        label
          ? 'flex h-11 min-w-[46px] flex-col items-center justify-center gap-0.5 rounded px-1.5 text-[11px] font-medium transition'
          : 'inline-flex h-8 w-8 items-center justify-center rounded transition',
        active
          ? 'bg-blue-100 text-blue-700 ring-1 ring-inset ring-blue-200'
          : 'text-slate-600 hover:bg-slate-100 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent',
      ].join(' ')}
    >
      {icon && <Icon name={icon} />}
      {label || null}
    </button>
  )
}

/** Commands that change how you work on the plan, as opposed to the plan itself. */
export function Ribbon({ canvasSize }: { canvasSize: { width: number; height: number } }) {
  const tool = useEditor((s) => s.tool)
  const setTool = useEditor((s) => s.setTool)
  const snap = useEditor((s) => s.snap)
  const setSnap = useEditor((s) => s.setSnap)
  const showGrid = useEditor((s) => s.showGrid)
  const toggleGrid = useEditor((s) => s.toggleGrid)
  const showText = useEditor((s) => s.showText)
  const toggleText = useEditor((s) => s.toggleText)
  const viewport = useEditor((s) => s.viewport)
  const zoomAtPoint = useEditor((s) => s.zoomAtPoint)
  const setZoom = useEditor((s) => s.setZoom)
  const fitToView = useEditor((s) => s.fitToView)
  const viewMode = useEditor((s) => s.viewMode)
  const setViewMode = useEditor((s) => s.setViewMode)
  const opacity = useEditor((s) => s.backgroundOpacity)
  const setOpacity = useEditor((s) => s.setBackgroundOpacity)
  const hasImage = useEditor((s) => Boolean(s.plan.source))

  const centre = { x: canvasSize.width / 2, y: canvasSize.height / 2 }
  // A "100%" of one screen pixel per millimetre would be absurd, so the readout
  // is relative to a comfortable 1:50-ish working scale.
  const percent = Math.round((viewport.scale / 0.05) * 100)
  const iconBtn = 'inline-flex h-8 w-8 items-center justify-center rounded text-slate-600 hover:bg-slate-100'

  return (
    <div className="flex shrink-0 items-stretch border-b border-slate-200 bg-slate-50 py-1.5">
      {/* Scrolls sideways on narrow screens; the shortcuts popover stays outside it so it is not clipped. */}
      <div className="flex min-w-0 flex-1 items-stretch overflow-x-auto">
      <Group label="Tools">
        {TOOLS.map((t) => (
          <button
            key={t.id}
            type="button"
            title={`${t.hint} (${t.shortcut})`}
            aria-pressed={tool === t.id}
            onClick={() => setTool(t.id)}
            className={[
              'flex h-11 min-w-[46px] flex-col items-center justify-center gap-0.5 rounded px-1.5 text-[11px] font-medium transition',
              tool === t.id ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-700 hover:bg-slate-100',
            ].join(' ')}
          >
            <Icon name={t.icon} size={18} />
            {t.label}
          </button>
        ))}
      </Group>

      <Group label="Snapping">
        <ToggleButton
          active={snap.orthogonal}
          onClick={() => setSnap({ orthogonal: !snap.orthogonal })}
          icon="ortho"
          label="Ortho"
          title="Keep walls horizontal or vertical. Shift does this for one wall."
        />
        <ToggleButton
          active={snap.grid > 0}
          onClick={() => setSnap({ grid: snap.grid > 0 ? 0 : 50 })}
          icon="magnet"
          label="50 mm"
          title="Snap points to a 50 mm grid"
        />
        <label className="flex items-center pl-1" title="Snap radius: how close a point must be to snap to a wall end">
          <select
            aria-label="Snap radius"
            value={snap.radius}
            onChange={(e) => setSnap({ radius: Number(e.target.value) })}
            className="h-7 rounded border border-slate-200 bg-white px-1 text-[12px] text-slate-700 focus:border-blue-500 focus:outline-none"
          >
            {[0, 100, 150, 250, 400, 600].map((r) => (
              <option key={r} value={r}>
                {r === 0 ? 'Radius off' : `Radius ${r}`}
              </option>
            ))}
          </select>
        </label>
      </Group>

      <Group label="View">
        <div className="flex rounded border border-slate-200 bg-white p-0.5">
          {MODES.map((m) => {
            const enabled = hasImage || m.id === 'reconstruction'
            return (
              <button
                key={m.id}
                type="button"
                title={enabled ? m.hint : 'No source image for this plan'}
                disabled={!enabled}
                aria-pressed={viewMode === m.id}
                onClick={() => setViewMode(m.id)}
                className={[
                  'h-7 rounded-sm px-2.5 text-[12px] font-medium transition',
                  viewMode === m.id
                    ? 'bg-slate-800 text-white'
                    : enabled
                      ? 'text-slate-600 hover:bg-slate-100'
                      : 'cursor-not-allowed text-slate-300',
                ].join(' ')}
              >
                {m.label}
              </button>
            )
          })}
        </div>
        {hasImage && viewMode === 'overlay' && (
          <label className="flex items-center gap-1.5 pl-1 text-[12px] text-slate-500" title="How strongly the original image shows through">
            <Icon name="image" />
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={opacity}
              onChange={(e) => setOpacity(Number(e.target.value))}
              className="w-16 accent-[var(--accent)]"
            />
            <span className="w-8 tabular-nums">{Math.round(opacity * 100)}%</span>
          </label>
        )}
      </Group>

      <Group label="Show">
        <ToggleButton active={showText} onClick={toggleText} icon="text" label="" title="Labels: room names, dimensions and other text read from the plan (T)" />
        <ToggleButton active={showGrid} onClick={toggleGrid} icon="grid" label="" title="1 m grid" />
      </Group>

      <Group label="Zoom">
        <button type="button" className={iconBtn} title="Zoom out" aria-label="Zoom out" onClick={() => zoomAtPoint(centre, 1 / 1.25)}>
          <Icon name="zoomOut" />
        </button>
        <button
          type="button"
          title="Back to 100%"
          onClick={() => setZoom(0.05, canvasSize.width, canvasSize.height)}
          className="h-7 w-14 rounded border border-slate-200 bg-white text-center text-[12px] tabular-nums text-slate-700 hover:bg-slate-100"
        >
          {percent}%
        </button>
        <button type="button" className={iconBtn} title="Zoom in" aria-label="Zoom in" onClick={() => zoomAtPoint(centre, 1.25)}>
          <Icon name="zoomIn" />
        </button>
        <button type="button" className={iconBtn} title="Fit the plan to the window" aria-label="Fit" onClick={() => fitToView(canvasSize.width, canvasSize.height)}>
          <Icon name="fit" />
        </button>
      </Group>
      </div>

      <div className="relative flex shrink-0 items-center border-l border-slate-200 px-3">
        <details className="group">
          <summary className="flex h-8 cursor-pointer list-none items-center gap-1.5 rounded px-2 text-[12.5px] font-medium text-slate-600 hover:bg-slate-100">
            <Icon name="keyboard" />
            Shortcuts
          </summary>
          <div className="absolute right-3 top-11 z-10 w-72 rounded-lg border border-slate-200 bg-white p-3 shadow-xl">
            <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-xs">
              {SHORTCUTS.map(([keys, what]) => (
                <div key={keys} className="contents">
                  <dt className="font-mono text-[11px] text-slate-700">{keys}</dt>
                  <dd className="text-slate-500">{what}</dd>
                </div>
              ))}
            </dl>
          </div>
        </details>
      </div>
    </div>
  )
}
