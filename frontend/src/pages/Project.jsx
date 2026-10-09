import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useCompany } from '../context/CompanyContext'
import AppLayout from '../components/AppLayout'
import CompanyGate from '../components/CompanyGate'
import Icon from '../components/Icon'
import StageBadge from '../components/StageBadge'
import ProjectFields, { formFor, toRequest } from '../components/projects/ProjectFields'
import {
  DWELLING_LABELS, STATUS_LABELS, addHouse, ago, archiveProject, fieldErrors, getProject, restoreProject, updateProject,
} from '../api/projects'

/** One project: its details (editable), its houses, and adding houses. */
export default function Project() {
  const { id } = useParams()
  const { can, company } = useCompany()
  const [state, setState] = useState({ loading: true })
  const [editing, setEditing] = useState(false)

  const load = useCallback(() => {
    setState(s => ({ ...s, loading: true }))
    return getProject(id)
      .then(project => setState({ project }))
      .catch(e => setState({ error: e.message, notFound: e.status === 404 }))
  }, [id])

  // Load once the company is known (the API needs it), and again when it changes.
  useEffect(() => {
    if (company) load()
  }, [company, load])

  const project = state.project
  const mayEdit = can('project.edit') && project && !project.archived

  const toggleArchive = async () => {
    const changed = project.archived ? await restoreProject(project.id) : await archiveProject(project.id)
    setState({ project: changed })
    setEditing(false)
  }

  const actions = project && can('project.edit') && (
    <>
      {mayEdit && !editing && <button type="button" className="btn" onClick={() => setEditing(true)}>Edit details</button>}
      <button type="button" className="btn ghost" onClick={toggleArchive}>{project.archived ? 'Restore' : 'Archive'}</button>
    </>
  )

  return (
    <AppLayout title={project ? project.name : 'Project'} actions={actions}>
      <CompanyGate>
        <nav className="crumbs" aria-label="Breadcrumb">
          <Link to="/">Projects</Link><span>/</span><span>{project ? project.reference : '…'}</span>
        </nav>
        {state.error && (
          <div className="callout bad" role="alert">
            {state.notFound ? 'This project does not exist in your company, or it was removed.' : state.error}
          </div>
        )}
        {project && project.archived && (
          <div className="callout warn">This project is archived: it is read-only until restored.</div>
        )}
        {project && (editing
          ? <EditProject project={project} onSaved={p => { setState({ project: p }); setEditing(false) }}
              onCancel={() => setEditing(false)} onReload={() => { setEditing(false); load() }} />
          : <ProjectDetails project={project} />)}
        {project && <Houses project={project} mayAdd={mayEdit} onAdded={load} />}
      </CompanyGate>
    </AppLayout>
  )
}

function ProjectDetails({ project }) {
  const site = [project.site.street, project.site.suburb, project.site.state, project.site.postcode].filter(Boolean).join(', ')
  return (
    <section className="panel">
      <div className="pnh">
        <h3>Details</h3>
        <span className="meta">{project.reference} · last activity {ago(project.lastActivityAt)}</span>
      </div>
      <div className="pnb">
        <div className="kv">
          <span className="k">Status</span><span className="v">{STATUS_LABELS[project.status]}</span>
          <span className="k">Site</span><span className="v">{site}</span>
          {project.lotNumber && <><span className="k">Lot</span><span className="v">{project.lotNumber}</span></>}
          <span className="k">Distributor</span><span className="v">{project.distributor ?? 'Not known yet'}</span>
          <span className="k">Supply</span><span className="v">{project.supplyPhases === 3 ? 'Three-phase' : 'Single-phase'}</span>
          {project.dueOn && <><span className="k">Due</span><span className="v">{project.dueOn}</span></>}
          {project.description && <><span className="k">Notes</span><span className="v">{project.description}</span></>}
        </div>
      </div>
    </section>
  )
}

/** The details as a form. A project changed by someone else meanwhile is not overwritten: reload first. */
function EditProject({ project, onSaved, onCancel, onReload }) {
  const [form, setForm] = useState(formFor(project))
  const [errors, setErrors] = useState({})
  const [problem, setProblem] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async e => {
    e.preventDefault()
    setBusy(true)
    setProblem(null)
    try {
      onSaved(await updateProject(project.id, toRequest(form)))
    } catch (err) {
      setErrors(fieldErrors(err))
      setProblem({ message: err.message, stale: err.status === 409 })
      setBusy(false)
    }
  }

  return (
    <form className="panel form" onSubmit={submit} noValidate>
      {problem && (
        <div className="callout bad" role="alert">
          <span>{problem.message}</span>
          {problem.stale && <button type="button" className="btn" onClick={onReload}>Reload</button>}
        </div>
      )}
      <ProjectFields form={form} onChange={setForm} errors={errors} showStatus />
      <div className="form-actions">
        <button type="button" className="btn ghost" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn pri" disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</button>
      </div>
    </form>
  )
}

function Houses({ project, mayAdd, onAdded }) {
  const current = project.houses.filter(h => !h.archived)
  return (
    <section className="panel" aria-labelledby="houses">
      <div className="pnh">
        <h3 id="houses">Houses</h3>
        <span className="meta">{current.length} {current.length === 1 ? 'house' : 'houses'}</span>
      </div>
      {current.length === 0
        ? (
          <div className="empty">
            <span className="empty-icon"><Icon name="home" size={22} /></span>
            <h4>No houses yet</h4>
            <p>Add each house design in this project. Its floor plan and electrical design are kept with it.</p>
          </div>
        )
        : (
          <table className="table">
            <thead><tr><th>House</th><th>Type</th><th>Stage</th><th className="right">Changed</th><th /></tr></thead>
            <tbody>
              {current.map(h => (
                <tr key={h.id}>
                  <td><b>{h.name}</b></td>
                  <td>{DWELLING_LABELS[h.dwellingType]}</td>
                  <td><StageBadge stage={h.stage} /></td>
                  <td className="right meta">{ago(h.updatedAt)}</td>
                  <td className="right">
                    <Link to={`/projects/${project.id}/houses/${h.id}/floor-plan`} className="btn">
                      <Icon name="plan" size={16} />Floor plan
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      {mayAdd && <AddHouse projectId={project.id} onAdded={onAdded} />}
    </section>
  )
}

function AddHouse({ projectId, onAdded }) {
  const [name, setName] = useState('')
  const [dwellingType, setDwellingType] = useState('house')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async e => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await addHouse(projectId, { name: name.trim(), dwellingType })
      setName('')
      await onAdded()
    } catch (err) {
      setError(fieldErrors(err).name ?? err.message)
    }
    setBusy(false)
  }

  return (
    <form className="add-row" onSubmit={submit} noValidate>
      <input className="in" aria-label="House name" placeholder="House name, e.g. Lot 12 – Type A" value={name}
        onChange={e => setName(e.target.value)} aria-invalid={!!error} />
      <select className="in" aria-label="Dwelling type" value={dwellingType} onChange={e => setDwellingType(e.target.value)}>
        {Object.entries(DWELLING_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
      </select>
      <button type="submit" className="btn pri" disabled={busy}><Icon name="plus" size={16} />Add house</button>
      {error && <span className="fld-error" role="alert">{error}</span>}
    </form>
  )
}
