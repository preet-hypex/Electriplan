import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import brand from '../lib/brand'
import { invitationState } from '../lib/invitation'
import { authRedirectError } from '../lib/supabase'
import AuthLayout from '../components/AuthLayout'
import Logo from '../components/Logo'

export default function Invite() {
  const { user, loading, acceptInvitation } = useAuth()
  const navigate = useNavigate()

  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (loading) {
    return (
      <AuthLayout>
        <div className="card"><Logo large /><div className="sub">Checking your invitation…</div></div>
      </AuthLayout>
    )
  }

  if (user && invitationState(user) !== 'pending') return <Navigate to="/" replace />

  if (!user) {
    return (
      <AuthLayout>
        <div className="card">
          <Logo large />
          <h1>Invitation only</h1>
          {authRedirectError
            ? <div className="callout bad">{authRedirectError}. Ask an administrator to send you a new invitation.</div>
            : <div className="sub">{brand.name} accounts are created by invitation. Open the link in your invitation email to set up your account.</div>}
          <div className="switch">Already set up? <Link to="/login">Sign in</Link></div>
        </div>
      </AuthLayout>
    )
  }

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    if (!fullName.trim()) return setError('Please enter your name.')
    if (password.length < 8) return setError('Password must be at least 8 characters.')
    if (password !== confirm) return setError('Passwords do not match.')

    setBusy(true)
    try {
      const { error } = await acceptInvitation(fullName.trim(), password)
      if (error) throw error
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message || 'Could not finish setting up your account')
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <form className="card" onSubmit={onSubmit} noValidate>
        <Logo large />
        <h1>Set up your account</h1>
        <div className="sub">
          You were invited as <b>{user.email}</b>. Choose the name your colleagues will see, and a password.
        </div>

        {error && <div className="callout bad">{error}</div>}

        <input type="email" name="username" autoComplete="username" value={user.email ?? ''} readOnly hidden />

        <div className="fld">
          <label htmlFor="name">Full name</label>
          <input id="name" className="in" value={fullName} onChange={e => setFullName(e.target.value)} placeholder="Your name" autoComplete="name" autoFocus required />
        </div>
        <div className="fld">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" className="in" value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 8 characters" autoComplete="new-password" minLength={8} required />
        </div>
        <div className="fld">
          <label htmlFor="confirm">Confirm password</label>
          <input id="confirm" type="password" className="in" value={confirm} onChange={e => setConfirm(e.target.value)} placeholder="Repeat your password" autoComplete="new-password" required />
        </div>

        <button className="btn pri block" type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Finish setting up'} {!busy && <span className="kbd">⏎</span>}
        </button>
      </form>
    </AuthLayout>
  )
}
