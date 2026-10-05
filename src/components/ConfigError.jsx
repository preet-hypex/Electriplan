import Logo from './Logo'

export default function ConfigError({ reason }) {
  return (
    <div className="auth-main" style={{ minHeight: '100vh' }}>
      <div className="card">
        <Logo large />
        <h1>Configuration missing</h1>
        <div className="sub">The app cannot reach Supabase Auth, so it cannot sign anyone in.</div>

        <div className="callout bad"><span>{reason}</span></div>

        <p style={{ fontSize: '12.5px', color: 'var(--body)', lineHeight: 1.6, margin: '0 0 12px' }}>
          Sign-in runs against Supabase in the browser. The setup script fetches the project URL and
          publishable key for you and writes them to <b>.env</b>.
        </p>

        <pre className="code" style={{ marginBottom: 14 }}>{`npm run setup
npm run dev`}</pre>

        <p className="hint">
          Vite only reads <b>.env</b> at startup, so restart the dev server after running setup.
          On a fresh clone, <b>npm run setup -- sync</b> rebuilds it from the keychain.
        </p>
      </div>
    </div>
  )
}
