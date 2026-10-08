import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useCompany } from '../context/CompanyContext'
import brand from '../lib/brand'
import Icon from './Icon'
import Logo from './Logo'

// What the product does, in workflow order. Only sections that exist are links;
// the rest are shown so people can see where the product is going.
const NAV = [
  { to: '/', label: 'Projects', icon: 'folder', end: true, also: '/projects' },
  { to: '/floor-plan', label: 'Floor-plan editor', icon: 'plan' },
  { label: 'Electrical design', icon: 'bolt', soon: true },
  { label: 'Reviews', icon: 'review', soon: true },
  { label: 'Quotes', icon: 'quote', soon: true },
]

export function displayName(user) {
  return user.user_metadata?.full_name || user.email?.split('@')[0] || 'there'
}

export function initials(user) {
  const name = user.user_metadata?.full_name || user.email || '?'
  return name.split(/[\s@.]+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('')
}

const SIDEBAR_KEY = 'electriplan.sidebar'

// Remembered per browser. Storage can be unavailable (private windows, blocked
// site data), so every read and write is allowed to fail.
function readCollapsed() {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === 'collapsed'
  } catch {
    return false
  }
}

function writeCollapsed(collapsed) {
  try {
    localStorage.setItem(SIDEBAR_KEY, collapsed ? 'collapsed' : 'expanded')
  } catch {
    // Not remembered; the toggle still works for this visit.
  }
}

/** The company you are working in; a choice when you belong to several. */
function CompanySwitcher() {
  const { status, company, companies, switchTo } = useCompany()
  if (status !== 'ready' || !company) return null
  if (companies.length < 2) {
    return <div className="side-company" title={company.name}><span className="side-company-name">{company.name}</span></div>
  }
  return (
    <div className="side-company">
      <select className="side-company-pick" aria-label="Company" value={company.id} onChange={e => switchTo(e.target.value)}>
        {companies.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
      </select>
    </div>
  )
}

/**
 * The frame around every signed-in page. `fill` is for tools such as the
 * floor-plan editor: the page is exactly the window's height and the content
 * area has no padding, so the tool owns all of it.
 */
export default function AppLayout({ title, actions, fill = false, children }) {
  const { user, signOut } = useAuth()
  const { pathname } = useLocation()
  const [collapsed, setCollapsed] = useState(readCollapsed)

  const toggleSidebar = () => {
    setCollapsed(c => {
      writeCollapsed(!c)
      return !c
    })
  }

  useEffect(() => {
    document.title = `${title} · ${brand.name}`
  }, [title])

  return (
    <div className={`shell${fill ? ' fill' : ''}${collapsed ? ' collapsed' : ''}`}>
      <aside className="side" id="app-sidebar">
        <NavLink to="/" className="side-brand"><Logo /></NavLink>
        <CompanySwitcher />

        <nav className="side-nav" aria-label="Main">
          <div className="side-label">Workspace</div>
          {NAV.map(item => item.soon
            ? (
              <span key={item.label} className="side-link soon" aria-disabled="true" title={`${item.label} — coming soon`}>
                <Icon name={item.icon} /><span>{item.label}</span><span className="soon-tag">Soon</span>
              </span>
            )
            : (
              <NavLink key={item.label} to={item.to} end={item.end} title={item.label}
                className={({ isActive }) => `side-link${isActive || (item.also && pathname.startsWith(item.also)) ? ' active' : ''}`}>
                <Icon name={item.icon} /><span>{item.label}</span>
              </NavLink>
            ))}
        </nav>

        <div className="side-foot">
          <NavLink to="/account" className="side-link" title="Account"><Icon name="user" /><span>Account</span></NavLink>
          <div className="side-user" title={collapsed ? user.email : undefined}>
            <div className="avatar">{initials(user)}</div>
            <div className="side-who">
              <div className="side-name">{displayName(user)}</div>
              <div className="side-email">{user.email}</div>
            </div>
            <button className="icon-btn" onClick={signOut} title="Sign out" aria-label="Sign out">
              <Icon name="signout" />
            </button>
          </div>
        </div>
      </aside>

      <div className="shell-main">
        <header className="bar">
          <button
            type="button"
            className="burger"
            onClick={toggleSidebar}
            aria-controls="app-sidebar"
            aria-expanded={!collapsed}
            aria-label={collapsed ? 'Expand the menu' : 'Collapse the menu'}
            title={collapsed ? 'Expand the menu' : 'Collapse the menu'}
          >
            <Icon name="menu" />
          </button>
          <h1>{title}</h1>
          <div className="grow" />
          {actions}
        </header>
        <main className={fill ? 'content-fill' : 'content'}>{children}</main>
      </div>
    </div>
  )
}
