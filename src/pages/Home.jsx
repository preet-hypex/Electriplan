import { useAuth } from '../context/AuthContext'
import Logo from '../components/Logo'

function initials(user) {
  const name = user.user_metadata?.full_name || user.email || '?'
  return name.split(/[\s@.]+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('')
}

export default function Home() {
  const { user, signOut } = useAuth()
  const name = user.user_metadata?.full_name

  return (
    <>
      <header className="top">
        <Logo />
        <div className="grow" />
        <button className="btn ghost" onClick={signOut}>Sign out</button>
        <div className="avatar" title={user.email}>{initials(user)}</div>
      </header>
      <main className="page">
        <h1>Welcome{name ? `, ${name}` : ''}</h1>
        <section className="panel">
          <div className="pnh"><h3>Your session</h3><span className="meta">Signed in with Supabase Auth</span></div>
          <div className="pnb">
            <div className="kv">
              <span className="k">Email</span><span className="v">{user.email}</span>
              <span className="k">User ID</span><span className="v">{user.id}</span>
              <span className="k">Last sign-in</span><span className="v">{user.last_sign_in_at ? new Date(user.last_sign_in_at).toLocaleString() : '—'}</span>
            </div>
          </div>
        </section>
      </main>
    </>
  )
}
