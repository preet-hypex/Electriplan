import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useCompany } from '../context/CompanyContext'
import AppLayout, { displayName } from '../components/AppLayout'
import CompanyGate from '../components/CompanyGate'
import Icon from '../components/Icon'
import StageBadge from '../components/StageBadge'
import { STATUS_LABELS, ago, listProjects, recentHouses } from '../api/projects'

const PAGE_SIZE = 25

function greeting(date = new Date()) {
  const h = date.getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

/** Text typed into the search box, settled for a moment before searching. */
function useSettled(value, ms = 300) {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), ms)
    return () => clearTimeout(timer)
  }, [value, ms])
  return settled
}

/** The landing page after signing in: the company's projects, and where to pick up work. */
export default function Home() {
  const { user } = useAuth()
  const { company, can } = useCompany()
  const newProject = can('project.edit') && (
    <Link to="/projects/new" className="btn pri"><Icon name="plus" size={16} />New project</Link>
  )

  return (
    <AppLayout title="Projects" actions={newProject}>
      <section className="welcome">
        <p className="eyebrow">{greeting()}, {displayName(user)}</p>
        {company && <h2>{company.name}</h2>}
      </section>
      <CompanyGate>
        <RecentHouses />
        <ProjectList canCreate={can('project.edit')} />
      </CompanyGate>
    </AppLayout>
  )
}

/** "Continue where you left off": the houses changed most recently. Hidden until there are some. */
function RecentHouses() {
  const [houses, setHouses] = useState([])

  useEffect(() => {
    let current = true
    recentHouses(4).then(list => current && setHouses(list)).catch(() => {})
    return () => { current = false }
  }, [])

  if (houses.length === 0) return null
  return (
    <section aria-labelledby="recent">
      <div className="section-head"><h3 id="recent">Continue where you left off</h3></div>
      <ul className="recent">
        {houses.map(house => (
          <li key={house.id}>
            <Link to={`/projects/${house.project.id}/houses/${house.id}/floor-plan`} className="recent-card">
              <span className="recent-project">{house.project.reference} · {house.project.name}</span>
              <span className="recent-house">{house.name}</span>
              <span className="recent-foot"><StageBadge stage={house.stage} /><span className="meta">{ago(house.updatedAt)}</span></span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** The project list: search, status and archived filters, and pages. */
function ProjectList({ canCreate }) {
  const [words, setWords] = useState('')
  const [status, setStatus] = useState('')
  const [archived, setArchived] = useState(false)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState({ loading: true })
  const query = useSettled(words)

  // A new search starts from the first page.
  useEffect(() => setPage(0), [query, status, archived])

  useEffect(() => {
    let current = true
    setResult(r => ({ ...r, loading: true }))
    listProjects({ q: query, status: status || undefined, archived: archived || undefined, page, size: PAGE_SIZE })
      .then(data => current && setResult({ data }))
      .catch(e => current && setResult({ error: e.message }))
    return () => { current = false }
  }, [query, status, archived, page])

  const filtering = query !== '' || status !== '' || archived
  const data = result.data

  return (
    <section className="panel" aria-labelledby="projects">
      <div className="pnh">
        <h3 id="projects">{archived ? 'Archived projects' : 'Projects'}</h3>
        {data && <span className="meta">{data.total} {data.total === 1 ? 'project' : 'projects'}</span>}
      </div>
      <div className="toolbar">
        <input className="in search" type="search" placeholder="Search name, reference, street or suburb"
          aria-label="Search projects" value={words} onChange={e => setWords(e.target.value)} />
        <select className="in" aria-label="Status" value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">Any status</option>
          {Object.entries(STATUS_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
        </select>
        <label className="check">
          <input type="checkbox" checked={archived} onChange={e => setArchived(e.target.checked)} /> Archived
        </label>
      </div>

      {result.error && <div className="callout bad" role="alert">The projects could not be loaded: {result.error}</div>}
      {data && data.items.length === 0 && (filtering
        ? <div className="empty"><h4>No projects match</h4><p>Try other words, or clear the filters.</p></div>
        : <NoProjectsYet canCreate={canCreate} />)}
      {data && data.items.length > 0 && <ProjectTable items={data.items} />}
      {data && data.total > PAGE_SIZE && (
        <Pager page={page} size={PAGE_SIZE} total={data.total} onPage={setPage} />
      )}
      {result.loading && !data && <p className="hint pad">Loading projects…</p>}
    </section>
  )
}

function NoProjectsYet({ canCreate }) {
  return (
    <div className="empty">
      <span className="empty-icon"><Icon name="folder" size={22} /></span>
      <h4>No projects yet</h4>
      {canCreate
        ? (
          <>
            <p>A project is a job at one site. Add its houses, then their floor plans.</p>
            <Link to="/projects/new" className="btn pri"><Icon name="plus" size={16} />Start your first project</Link>
          </>
        )
        : <p>An owner, admin or builder in your company starts projects. They will appear here.</p>}
    </div>
  )
}

function ProjectTable({ items }) {
  return (
    <table className="table">
      <thead>
        <tr><th>Reference</th><th>Project</th><th>Site</th><th>Houses</th><th className="right">Last activity</th></tr>
      </thead>
      <tbody>
        {items.map(p => (
          <tr key={p.id}>
            <td className="mono">{p.reference}</td>
            <td>
              <Link to={`/projects/${p.id}`} className="row-link">{p.name}</Link>
              {p.status !== 'active' && <span className="status-tag">{STATUS_LABELS[p.status]}</span>}
            </td>
            <td>{[p.suburb, p.state].filter(Boolean).join(', ')}</td>
            <td>
              {p.houseCount === 0
                ? <span className="meta">None yet</span>
                : <span className="stages">{p.stages.map(s => <StageBadge key={s.stage} stage={s.stage} count={s.count} />)}</span>}
            </td>
            <td className="right meta">{ago(p.lastActivityAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function Pager({ page, size, total, onPage }) {
  const from = page * size + 1
  const to = Math.min(total, (page + 1) * size)
  return (
    <div className="pager">
      <span className="meta">{from}–{to} of {total}</span>
      <button type="button" className="btn" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button>
      <button type="button" className="btn" disabled={to >= total} onClick={() => onPage(page + 1)}>Next</button>
    </div>
  )
}
