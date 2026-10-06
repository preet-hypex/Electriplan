import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { invitationState } from '../lib/invitation'
import { forgetRecovery, isRecovering, sessionStore } from '../lib/recovery'
import { authRedirectError } from '../lib/supabase'
import AuthLayout from '../components/AuthLayout'
import Logo from '../components/Logo'

export default function ResetPassword() {
  const { user, loading, updatePassword } = useAuth()
  const navigate = useNavigate()

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (loading) {
    return (
      <AuthLayout>
        <div className="card"><Logo large /><div className="sub">Checking your reset link…</div></div>
      </AuthLayout>
    )
  }

  if (!user) {
    return (
      <AuthLayout>
        <div className="card">
          <Logo large />
          <h1>Reset password</h1>
          {authRedirectError
            ? <div className="callout bad">{authRedirectError}. Ask for a new link from the sign-in page.</div>
            : <div className="sub">Open the link in your password reset email to choose a new password.</div>}
          <div className="switch"><Link to="/login">Back to sign in</Link></div>
        </div>
      </AuthLayout>
    )
  }

  if (invitationState(user) === 'pending') return <Navigate to="/invite" replace />
  if (!isRecovering(sessionStore(), user.id)) return <Navigate to="/" replace />

  function done() {
    forgetRecovery(sessionStore())
    navigate('/', { replace: true })
  }

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    if (password.length < 8) return setError('Password must be at least 8 characters.')
    if (password !== confirm) return setError('Passwords do not match.')

    setBusy(true)
    try {
      const { error } = await updatePassword(password)
      if (error) throw error
      done()
    } catch (err) {
      setError(err.message || 'Could not change your password')
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <form className="card" onSubmit={onSubmit} noValidate>
        <Logo large />
        <h1>Choose a new password</h1>
        <div className="sub">For <b>{user.email}</b>. Other devices stay signed in.</div>

        {error && <div className="callout bad">{error}</div>}

        <input type="email" name="username" autoComplete="username" value={user.email ?? ''} readOnly hidden />

        <div className="fld">
          <label htmlFor="password">New password</label>
          <input id="password" type="password" className="in" value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 8 characters" autoComplete="new-password" minLength={8} required autoFocus />
        </div>
        <div className="fld">
          <label htmlFor="confirm">Confirm new password</label>
          <input id="confirm" type="password" className="in" value={confirm} onChange={e => setConfirm(e.target.value)} placeholder="Repeat your password" autoComplete="new-password" required />
        </div>

        <button className="btn pri block" type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Change password'} {!busy && <span className="kbd">⏎</span>}
        </button>

        <div className="switch">
          <a href="#" onClick={e => { e.preventDefault(); done() }}>Keep my current password</a>
        </div>
      </form>
    </AuthLayout>
  )
}
