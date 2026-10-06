import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { authRedirectError } from '../lib/supabase'
import AuthLayout from '../components/AuthLayout'
import Logo from '../components/Logo'

export default function Login() {
  const { user, loading, signIn, resetPassword } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from?.pathname ? `${location.state.from.pathname}${location.state.from.search ?? ''}` : '/'

  const [mode, setMode] = useState('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  if (!loading && user) return <Navigate to={from} replace />

  function switchMode(next) {
    setMode(next); setError(''); setNotice('')
  }

  async function onSubmit(e) {
    e.preventDefault()
    setBusy(true); setError(''); setNotice('')
    try {
      if (mode === 'signin') {
        const { error } = await signIn(email.trim(), password)
        if (error) throw error
        navigate(from, { replace: true })
      } else {
        if (!email.trim()) throw new Error('An email address is required')
        const { error } = await resetPassword(email.trim())
        if (error) throw error
        setNotice('If that address has an account, a reset link is on its way.')
      }
    } catch (err) {
      setError(err.message || 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <form className="card" onSubmit={onSubmit} noValidate>
        <Logo large />
        <h1>{mode === 'signin' ? 'Sign in' : 'Reset password'}</h1>
        <div className="sub">
          {mode === 'signin' ? 'Use your work email to continue.' : "We'll email you a link to set a new password."}
        </div>

        {error
          ? <div className="callout bad">{error}</div>
          : authRedirectError && mode === 'signin' && <div className="callout bad">{authRedirectError}</div>}
        {notice && <div className="callout ok">{notice}</div>}

        <div className="fld">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" className="in" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@company.com" autoComplete="email" required autoFocus />
        </div>

        {mode === 'signin' && (
          <div className="fld">
            <div className="row">
              <label htmlFor="password">Password</label>
              <a href="#" onClick={e => { e.preventDefault(); switchMode('reset') }}>Forgot?</a>
            </div>
            <input id="password" type="password" className="in" value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" autoComplete="current-password" required />
          </div>
        )}

        <button className="btn pri block" type="submit" disabled={busy}>
          {busy ? 'Working…' : mode === 'signin' ? 'Sign in' : 'Send reset link'} {!busy && <span className="kbd">⏎</span>}
        </button>

        <div className="switch">
          {mode === 'signin'
            ? 'Accounts are by invitation. Ask an administrator if you need one.'
            : <a href="#" onClick={e => { e.preventDefault(); switchMode('signin') }}>Back to sign in</a>}
        </div>
      </form>
    </AuthLayout>
  )
}
