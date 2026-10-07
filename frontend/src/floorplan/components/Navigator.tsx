import { useMemo, useState } from 'react'
import { useEditor, type Selection } from '../state/store'
import { bandColour, bandOf } from '../model/confidence'
import { roomAreaM2 } from '../model/metrics'
import { wallLength } from '../geometry/wall'
import type { Detected } from '../model/types'
import { Icon, type IconName } from './icons'

type Kind = NonNullable<Selection>['kind']

interface Item {
  id: string
  title: string
  meta: string
  detected: Detected
}

interface Section {
  kind: Kind
  label: string
  icon: IconName
  items: Item[]
}

const DOOR_STYLE = { swing: 'Swing', sliding: 'Sliding', garage: 'Garage' } as const

/** Every object in the plan, grouped by kind, so any of them can be found and selected by name. */
export function Navigator() {
  const plan = useEditor((s) => s.plan)
  const selection = useEditor((s) => s.selection)
  const select = useEditor((s) => s.select)
  const setTool = useEditor((s) => s.setTool)
  const [query, setQuery] = useState('')
  const [collapsed, setCollapsed] = useState<Set<Kind>>(() => new Set(['label']))

  const sections: Section[] = useMemo(
    () => [
      {
        kind: 'room',
        label: 'Rooms',
        icon: 'room',
        items: plan.rooms.map((r) => ({
          id: r.id,
          title: r.name.trim() || 'Unnamed room',
          meta: `${roomAreaM2(r).toFixed(1)} m²`,
          detected: r,
        })),
      },
      {
        kind: 'wall',
        label: 'Walls',
        icon: 'wall',
        items: plan.walls.map((w) => ({
          id: w.id,
          title: w.id,
          meta: `${(wallLength(w) / 1000).toFixed(2)} m · ${Math.round(w.thickness)}`,
          detected: w,
        })),
      },
      {
        kind: 'door',
        label: 'Doors',
        icon: 'door',
        items: plan.doors.map((d) => ({
          id: d.id,
          title: `${d.id}`,
          meta: `${DOOR_STYLE[d.style ?? 'swing']} · ${Math.round(d.width)}`,
          detected: d,
        })),
      },
      {
        kind: 'window',
        label: 'Windows',
        icon: 'window',
        items: plan.windows.map((w) => ({ id: w.id, title: w.id, meta: `${Math.round(w.width)}`, detected: w })),
      },
      {
        kind: 'opening',
        label: 'Openings',
        icon: 'opening',
        items: plan.openings.map((o) => ({ id: o.id, title: o.id, meta: `${Math.round(o.width)}`, detected: o })),
      },
      {
        kind: 'label',
        label: 'Labels',
        icon: 'label',
        items: plan.labels.map((l) => ({ id: l.id, title: l.text || l.id, meta: l.type, detected: l })),
      },
    ],
    [plan],
  )

  const q = query.trim().toLowerCase()
  const match = (i: Item) => !q || i.title.toLowerCase().includes(q) || i.id.toLowerCase().includes(q)

  const toggle = (kind: Kind) =>
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(kind)) next.delete(kind)
      else next.add(kind)
      return next
    })

  return (
    <aside className="flex w-60 shrink-0 flex-col border-r border-slate-200 bg-white" aria-label="Plan objects">
      <div className="border-b border-slate-200 p-2.5">
        <p className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wider text-slate-500">Navigator</p>
        <label className="flex h-8 items-center gap-2 rounded border border-slate-200 bg-slate-50 px-2 text-slate-400 focus-within:border-blue-500 focus-within:bg-white">
          <Icon name="search" size={14} />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Find a room, wall, door…"
            aria-label="Find an object"
            className="min-w-0 flex-1 bg-transparent text-[12.5px] text-slate-700 focus:outline-none"
          />
        </label>
      </div>

      <div className="flex-1 overflow-y-auto py-1">
        {sections.map((section) => {
          const items = section.items.filter(match)
          if (q && items.length === 0) return null
          const open = q ? true : !collapsed.has(section.kind)
          return (
            <section key={section.kind}>
              <button
                type="button"
                onClick={() => toggle(section.kind)}
                aria-expanded={open}
                className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-[12px] font-semibold text-slate-700 hover:bg-slate-50"
              >
                <Icon name="chevron" size={12} className={`text-slate-400 transition ${open ? 'rotate-90' : ''}`} />
                <Icon name={section.icon} size={14} className="text-slate-500" />
                {section.label}
                <span className="ml-auto rounded-full bg-slate-100 px-1.5 text-[10.5px] font-medium tabular-nums text-slate-500">
                  {section.items.length}
                </span>
              </button>
              {open &&
                (items.length === 0 ? (
                  <p className="py-1 pl-10 pr-3 text-[11.5px] text-slate-400">None</p>
                ) : (
                  <ul>
                    {items.map((item) => {
                      const selected = selection?.kind === section.kind && selection.id === item.id
                      const band = bandOf(item.detected)
                      return (
                        <li key={item.id}>
                          <button
                            type="button"
                            aria-current={selected || undefined}
                            onClick={() => {
                              setTool('select')
                              select({ kind: section.kind, id: item.id } as Selection)
                            }}
                            className={[
                              'flex w-full items-center gap-2 py-1 pl-10 pr-3 text-left text-[12px]',
                              selected ? 'bg-blue-50 text-blue-700' : 'text-slate-600 hover:bg-slate-50',
                            ].join(' ')}
                          >
                            <span
                              className="h-1.5 w-1.5 shrink-0 rounded-full"
                              style={{ background: band === 'unknown' ? 'var(--line-strong)' : bandColour(band) }}
                              title={band === 'unknown' ? 'Drawn or edited by hand' : `${band} confidence`}
                            />
                            <span className="min-w-0 flex-1 truncate">{item.title}</span>
                            <span className="shrink-0 tabular-nums text-[11px] text-slate-400">{item.meta}</span>
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                ))}
            </section>
          )
        })}
      </div>
    </aside>
  )
}
