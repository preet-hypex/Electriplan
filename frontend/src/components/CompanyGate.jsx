import { useCompany } from '../context/CompanyContext'
import Icon from './Icon'

/**
 * Shows its children once the company is known; otherwise says why not:
 * still loading, not a member of any company yet, or the API did not answer.
 */
export default function CompanyGate({ children }) {
  const { status, error, reload } = useCompany()

  if (status === 'loading') return <p className="hint">Loading your company…</p>
  if (status === 'none') {
    return (
      <section className="panel">
        <div className="empty">
          <span className="empty-icon"><Icon name="folder" size={22} /></span>
          <h4>You are not in a company yet</h4>
          <p>Projects belong to a company. Ask an owner or admin of your company to invite you, then sign in again.</p>
        </div>
      </section>
    )
  }
  if (status === 'error') {
    return (
      <div className="callout bad" role="alert">
        <span>Your company could not be loaded: {error}</span>
        <button type="button" className="btn" onClick={reload}>Try again</button>
      </div>
    )
  }
  return children
}
