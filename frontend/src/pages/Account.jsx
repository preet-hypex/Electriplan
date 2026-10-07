import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { api } from '../lib/api'
import AppLayout from '../components/AppLayout'

const when = value => (value ? new Date(value).toLocaleString() : '—')

function useCopyInPostgres(userId) {
  const [state, setState] = useState({ loading: true })

  useEffect(() => {
    let current = true
    setState({ loading: true })
    api('/api/me')
      .then(me => current && setState({ copy: me.copy }))
      .catch(e => current && setState({ error: e.message }))
    return () => { current = false }
  }, [userId])

  return state
}

/** Who you are signed in as, and what the API holds about you. */
export default function Account() {
  const { user } = useAuth()
  const postgres = useCopyInPostgres(user.id)

  return (
    <AppLayout title="Account">
      <section className="panel">
        <div className="pnh"><h3>Your session</h3><span className="meta">Signed in with Supabase Auth</span></div>
        <div className="pnb">
          <div className="kv">
            <span className="k">Email</span><span className="v">{user.email}</span>
            <span className="k">Name</span><span className="v">{user.user_metadata?.full_name ?? '—'}</span>
            <span className="k">User ID</span><span className="v">{user.id}</span>
            <span className="k">Last sign-in</span><span className="v">{when(user.last_sign_in_at)}</span>
          </div>
        </div>
      </section>
      <section className="panel">
        <div className="pnh"><h3>Copy in Postgres</h3><span className="meta">Read by the API at /api/me</span></div>
        <div className="pnb">
          {postgres.loading
            ? <p className="hint">Asking the API…</p>
            : postgres.error
              ? <div className="callout warn">The API did not answer: {postgres.error}</div>
              : postgres.copy
                ? (
                  <div className="kv">
                    <span className="k">Email</span><span className="v">{postgres.copy.email ?? '—'}</span>
                    <span className="k">Name</span><span className="v">{postgres.copy.userMetadata?.full_name ?? '—'}</span>
                    <span className="k">Last sign-in</span><span className="v">{when(postgres.copy.lastSignInAt)}</span>
                    <span className="k">Copied</span><span className="v">{when(postgres.copy.copiedAt)}</span>
                  </div>
                )
                : <p className="hint">Not copied yet. The API copies Supabase's users every few minutes.</p>}
        </div>
      </section>
    </AppLayout>
  )
}
