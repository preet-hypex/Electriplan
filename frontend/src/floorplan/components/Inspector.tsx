import { useState } from 'react'
import { useEditor, type Selection } from '../state/store'
import { planChecks, roomSchedule } from '../model/metrics'
import { bandColour, bandOf } from '../model/confidence'
import { PropertiesTab } from './PropertiesPanel'
import { Icon } from './icons'

type Tab = 'properties' | 'schedule' | 'checks'

/** The room schedule: every room with its area and perimeter, and the totals. */
function ScheduleTab() {
  const plan = useEditor((s) => s.plan)
  const selection = useEditor((s) => s.selection)
  const select = useEditor((s) => s.select)
  const schedule = roomSchedule(plan)

  if (schedule.rows.length === 0) {
    return <p className="text-xs text-slate-500">No rooms yet. Rooms appear here once walls enclose them.</p>
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
      <table className="w-full text-xs">
        <thead className="bg-slate-50 text-[10.5px] uppercase tracking-wider text-slate-500">
          <tr>
            <th className="px-2.5 py-2 text-left font-semibold">Room</th>
            <th className="px-2.5 py-2 text-right font-semibold">Area m²</th>
            <th className="px-2.5 py-2 text-right font-semibold">Perim. m</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {schedule.rows.map((row) => {
            const selected = selection?.kind === 'room' && selection.id === row.id
            const band = bandOf(row)
            return (
              <tr
                key={row.id}
                onClick={() => select({ kind: 'room', id: row.id })}
                className={`cursor-pointer ${selected ? 'bg-blue-50' : 'hover:bg-slate-50'}`}
              >
                <td className="px-2.5 py-1.5">
                  <span className="flex items-center gap-1.5">
                    <span
                      className="h-1.5 w-1.5 shrink-0 rounded-full"
                      style={{ background: band === 'unknown' ? 'var(--line-strong)' : bandColour(band) }}
                    />
                    <span className={row.name ? 'font-medium text-slate-800' : 'italic text-slate-400'}>
                      {row.name || 'Unnamed'}
                    </span>
                  </span>
                </td>
                <td className="px-2.5 py-1.5 text-right tabular-nums text-slate-700">{row.areaM2.toFixed(2)}</td>
                <td className="px-2.5 py-1.5 text-right tabular-nums text-slate-500">{row.perimeterM.toFixed(2)}</td>
              </tr>
            )
          })}
        </tbody>
        <tfoot className="border-t border-slate-200 bg-slate-50 font-semibold text-slate-800">
          <tr>
            <td className="px-2.5 py-2">Total · {schedule.rows.length} rooms</td>
            <td className="px-2.5 py-2 text-right tabular-nums">{schedule.totalAreaM2.toFixed(2)}</td>
            <td className="px-2.5 py-2 text-right tabular-nums">{schedule.totalPerimeterM.toFixed(2)}</td>
          </tr>
        </tfoot>
      </table>
      <p className="border-t border-slate-100 px-2.5 py-1.5 text-[11px] text-slate-400">
        Measured to the room outlines, at the plan's current scale.
      </p>
    </div>
  )
}

/** What to look at before trusting the plan. Clicking a check selects what it is about. */
function ChecksTab({ onSelect }: { onSelect: () => void }) {
  const plan = useEditor((s) => s.plan)
  const select = useEditor((s) => s.select)
  const checks = planChecks(plan)

  if (checks.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-800">
        <Icon name="check" />
        Nothing to review. Every object is confident, named and classified.
      </div>
    )
  }

  return (
    <ul className="flex flex-col gap-1.5">
      {checks.map((c) => {
        const content = (
          <>
            <Icon
              name={c.severity === 'warning' ? 'warning' : 'info'}
              className={`mt-0.5 shrink-0 ${c.severity === 'warning' ? 'text-amber-600' : 'text-slate-400'}`}
            />
            <span className="min-w-0">
              <span className="block font-medium text-slate-800">{c.title}</span>
              <span className="block text-slate-500">{c.detail}</span>
            </span>
          </>
        )
        const cls = 'flex w-full items-start gap-2 rounded-lg border border-slate-200 bg-white p-2.5 text-left text-xs'
        return (
          <li key={c.key}>
            {c.target ? (
              <button
                type="button"
                className={`${cls} hover:border-blue-200 hover:bg-blue-50`}
                onClick={() => {
                  select(c.target as Selection)
                  onSelect()
                }}
              >
                {content}
              </button>
            ) : (
              <div className={cls}>{content}</div>
            )}
          </li>
        )
      })}
    </ul>
  )
}

/** The right-hand panel: properties of the selection, the room schedule, and checks. */
export function Inspector() {
  const [tab, setTab] = useState<Tab>('properties')
  const checkCount = useEditor((s) => planChecks(s.plan).length)
  const warningCount = useEditor((s) => planChecks(s.plan).filter((c) => c.severity === 'warning').length)

  const tabs: Array<{ id: Tab; label: string; badge?: number }> = [
    { id: 'properties', label: 'Properties' },
    { id: 'schedule', label: 'Schedule' },
    { id: 'checks', label: 'Checks', badge: checkCount },
  ]

  return (
    <aside className="flex w-80 shrink-0 flex-col border-l border-slate-200 bg-slate-50" aria-label="Inspector">
      <div role="tablist" className="flex shrink-0 border-b border-slate-200 bg-white px-2">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={[
              '-mb-px flex items-center gap-1.5 border-b-2 px-3 py-2.5 text-[12.5px] font-medium',
              tab === t.id ? 'border-blue-600 text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-800',
            ].join(' ')}
          >
            {t.label}
            {t.badge ? (
              <span
                className={`rounded-full px-1.5 text-[10.5px] font-semibold tabular-nums ${
                  warningCount > 0 ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600'
                }`}
              >
                {t.badge}
              </span>
            ) : null}
          </button>
        ))}
      </div>
      <div className="flex-1 overflow-y-auto p-3">
        {tab === 'properties' && <PropertiesTab onShowChecks={() => setTab('checks')} />}
        {tab === 'schedule' && <ScheduleTab />}
        {tab === 'checks' && <ChecksTab onSelect={() => setTab('properties')} />}
      </div>
    </aside>
  )
}
