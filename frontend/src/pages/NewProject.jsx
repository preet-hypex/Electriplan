import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCompany } from '../context/CompanyContext'
import AppLayout from '../components/AppLayout'
import CompanyGate from '../components/CompanyGate'
import ProjectFields, { formFor, toRequest } from '../components/projects/ProjectFields'
import { createProject, fieldErrors } from '../api/projects'

/** Start a project: its name, site and supply. Then on to the project, to add its houses. */
export default function NewProject() {
  const { can } = useCompany()
  const navigate = useNavigate()
  const [form, setForm] = useState(formFor(null))
  const [errors, setErrors] = useState({})
  const [problem, setProblem] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async e => {
    e.preventDefault()
    setBusy(true)
    setProblem(null)
    try {
      const project = await createProject(toRequest(form))
      navigate(`/projects/${project.id}`)
    } catch (err) {
      setErrors(fieldErrors(err))
      setProblem(err.message)
      setBusy(false)
    }
  }

  return (
    <AppLayout title="New project">
      <CompanyGate>
        <nav className="crumbs" aria-label="Breadcrumb"><Link to="/">Projects</Link><span>/</span><span>New project</span></nav>
        {!can('project.edit')
          ? <div className="callout warn">Your role cannot start projects. Ask an owner, admin or builder.</div>
          : (
            <form className="panel form" onSubmit={submit} noValidate>
              {problem && <div className="callout bad" role="alert">{problem}</div>}
              <ProjectFields form={form} onChange={setForm} errors={errors} />
              <div className="form-actions">
                <Link to="/" className="btn ghost">Cancel</Link>
                <button type="submit" className="btn pri" disabled={busy}>{busy ? 'Creating…' : 'Create project'}</button>
              </div>
            </form>
          )}
      </CompanyGate>
    </AppLayout>
  )
}
