import { useEffect, useState } from 'react'
import { STATES, STATUS_LABELS, distributorsIn } from '../../api/projects'

/** An empty form, or the form for an existing project (with its version, for saving changes). */
export function formFor(project) {
  if (!project) {
    return {
      name: '', description: '', lotNumber: '', dueOn: '',
      site: { street: '', suburb: '', state: '', postcode: '' },
      distributor: '', supplyPhases: 1, status: 'active',
    }
  }
  return {
    name: project.name,
    description: project.description ?? '',
    lotNumber: project.lotNumber ?? '',
    dueOn: project.dueOn ?? '',
    site: {
      street: project.site.street ?? '',
      suburb: project.site.suburb ?? '',
      state: project.site.state,
      postcode: project.site.postcode ?? '',
    },
    distributor: project.distributor ?? '',
    supplyPhases: project.supplyPhases,
    status: project.status,
    version: project.version,
  }
}

/** What the API is sent: empty text is left out rather than sent as "". */
export function toRequest(form) {
  const text = value => (value && value.trim() ? value.trim() : undefined)
  return {
    name: form.name.trim(),
    description: text(form.description),
    lotNumber: text(form.lotNumber),
    dueOn: text(form.dueOn),
    site: {
      street: text(form.site.street),
      suburb: text(form.site.suburb),
      state: form.site.state || undefined,
      postcode: text(form.site.postcode),
    },
    distributor: text(form.distributor),
    supplyPhases: Number(form.supplyPhases),
    status: form.status,
    version: form.version,
  }
}

function Field({ id, label, error, hint, children }) {
  return (
    <div className={`fld${error ? ' has-error' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? <span className="fld-error" id={`${id}-error`}>{error}</span> : hint && <span className="fld-hint">{hint}</span>}
    </div>
  )
}

/**
 * The project's details as form fields. `form` and `onChange` hold the
 * values; `errors` are the API's field errors by field ("site.state").
 * The distributor list follows the chosen state.
 */
export default function ProjectFields({ form, onChange, errors = {}, showStatus = false }) {
  const [distributors, setDistributors] = useState([])
  const state = form.site.state

  useEffect(() => {
    let current = true
    if (!state) {
      setDistributors([])
      return undefined
    }
    distributorsIn(state).then(list => current && setDistributors(list)).catch(() => current && setDistributors([]))
    return () => { current = false }
  }, [state])

  const set = (key, value) => onChange({ ...form, [key]: value })
  const setSite = (key, value) => {
    const site = { ...form.site, [key]: value }
    // A new state has different distributors: the old choice no longer applies.
    onChange(key === 'state' ? { ...form, site, distributor: '' } : { ...form, site })
  }
  const input = (id, error) => ({ id, className: 'in', 'aria-invalid': !!error, 'aria-describedby': error ? `${id}-error` : undefined })

  return (
    <>
      <fieldset className="form-section">
        <legend>Project</legend>
        <Field id="name" label="Name" error={errors.name}>
          <input {...input('name', errors.name)} value={form.name} onChange={e => set('name', e.target.value)}
            placeholder="e.g. 12 Example Street" autoFocus required />
        </Field>
        <div className="form-row">
          <Field id="lotNumber" label="Lot number" error={errors.lotNumber}>
            <input {...input('lotNumber', errors.lotNumber)} value={form.lotNumber} onChange={e => set('lotNumber', e.target.value)} />
          </Field>
          <Field id="dueOn" label="Due" error={errors.dueOn}>
            <input {...input('dueOn', errors.dueOn)} type="date" value={form.dueOn} onChange={e => set('dueOn', e.target.value)} />
          </Field>
          {showStatus && (
            <Field id="status" label="Status" error={errors.status}>
              <select {...input('status', errors.status)} value={form.status} onChange={e => set('status', e.target.value)}>
                {Object.entries(STATUS_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
              </select>
            </Field>
          )}
        </div>
        <Field id="description" label="Notes" error={errors.description}>
          <textarea {...input('description', errors.description)} className="in area" rows={3} value={form.description}
            onChange={e => set('description', e.target.value)} />
        </Field>
      </fieldset>

      <fieldset className="form-section">
        <legend>Site</legend>
        <Field id="street" label="Street" error={errors['site.street']}>
          <input {...input('street', errors['site.street'])} value={form.site.street} onChange={e => setSite('street', e.target.value)}
            autoComplete="address-line1" />
        </Field>
        <div className="form-row">
          <Field id="suburb" label="Suburb" error={errors['site.suburb']}>
            <input {...input('suburb', errors['site.suburb'])} value={form.site.suburb} onChange={e => setSite('suburb', e.target.value)}
              autoComplete="address-level2" />
          </Field>
          <Field id="state" label="State" error={errors['site.state'] ?? errors.site}
            hint="Decides which rules design its houses">
            <select {...input('state', errors['site.state'] ?? errors.site)} value={form.site.state} onChange={e => setSite('state', e.target.value)} required>
              <option value="">Choose…</option>
              {STATES.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </Field>
          <Field id="postcode" label="Postcode" error={errors['site.postcode']}>
            <input {...input('postcode', errors['site.postcode'])} value={form.site.postcode} onChange={e => setSite('postcode', e.target.value)}
              inputMode="numeric" maxLength={4} autoComplete="postal-code" />
          </Field>
        </div>
      </fieldset>

      <fieldset className="form-section">
        <legend>Supply</legend>
        <div className="form-row">
          <Field id="distributor" label="Distributor" error={errors.distributor}
            hint={state && distributors.length === 0 ? `None set up in ${state} yet` : undefined}>
            <select {...input('distributor', errors.distributor)} value={form.distributor}
              onChange={e => set('distributor', e.target.value)} disabled={!state || distributors.length === 0}>
              <option value="">{state ? 'Not known yet' : 'Choose the state first'}</option>
              {distributors.map(d => <option key={d.code} value={d.code}>{d.name}</option>)}
            </select>
          </Field>
          <Field id="supplyPhases" label="Supply" error={errors.supplyPhases}>
            <select {...input('supplyPhases', errors.supplyPhases)} value={form.supplyPhases}
              onChange={e => set('supplyPhases', Number(e.target.value))}>
              <option value={1}>Single-phase</option>
              <option value={3}>Three-phase</option>
            </select>
          </Field>
        </div>
      </fieldset>
    </>
  )
}
