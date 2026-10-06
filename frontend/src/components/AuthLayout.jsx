import brand from '../lib/brand'
import Logo from './Logo'

export default function AuthLayout({ children }) {
  const [first, ...rest] = brand.headline.split('\n')
  return (
    <div className="auth">
      <aside className="auth-side">
        <Logo />
        <div>
          <h2>{first}{rest.map((line, i) => <span key={i}><br />{line}</span>)}</h2>
          {brand.blurb && <p>{brand.blurb}</p>}
          {brand.highlights.length > 0 && (
            <div className="pills">
              {brand.highlights.map(h => <span className="pill" key={h}>{h}</span>)}
            </div>
          )}
        </div>
        <div className="foot">{brand.footer || `${brand.name} · ${new Date().getFullYear()}`}</div>
      </aside>
      <main className="auth-main">{children}</main>
    </div>
  )
}
