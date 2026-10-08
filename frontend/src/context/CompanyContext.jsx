import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { useAuth } from './AuthContext'
import { setCompany } from '../lib/api'
import { currentCompany, myCompanies } from '../api/projects'

const STORAGE_KEY = 'electriplan.company'

// Remembered per browser; storage can be unavailable, so reads and writes may fail.
function remembered() {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function remember(id) {
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // Not remembered; the choice still holds for this visit.
  }
}

/**
 * What the app knows before anyone has loaded anything: used when a page is
 * rendered without the provider (tests), and while loading.
 */
const NOTHING_YET = {
  status: 'loading', // loading | ready | none | error
  companies: [],
  company: null, // {id, name, slug, role, licence, permissions}
  error: null,
  can: () => false,
  switchTo: () => {},
  reload: () => {},
}

const CompanyContext = createContext(NOTHING_YET)

/**
 * The company the signed-in person is working in. Picks the one they chose
 * last time (or their first), tells lib/api to send it with every call, and
 * loads their role's permissions there, so pages show only what will work.
 */
export function CompanyProvider({ children }) {
  const { user } = useAuth()
  const [state, setState] = useState(NOTHING_YET)

  const load = useCallback(async (preferredId) => {
    setState(s => ({ ...s, status: 'loading', error: null }))
    try {
      const companies = await myCompanies()
      if (companies.length === 0) {
        setCompany(null)
        setState({ ...NOTHING_YET, status: 'none' })
        return
      }
      const chosen = companies.find(c => c.id === preferredId) ?? companies[0]
      setCompany(chosen.id)
      remember(chosen.id)
      const company = await currentCompany()
      setState({ ...NOTHING_YET, status: 'ready', companies, company })
    } catch (e) {
      setState({ ...NOTHING_YET, status: 'error', error: e.message })
    }
  }, [])

  useEffect(() => {
    if (user) {
      load(remembered())
    } else {
      setCompany(null)
      setState(NOTHING_YET)
    }
  }, [user, load])

  const value = {
    ...state,
    can: permission => state.company?.permissions?.includes(permission) ?? false,
    switchTo: id => load(id),
    reload: () => load(state.company?.id ?? remembered()),
  }
  return <CompanyContext.Provider value={value}>{children}</CompanyContext.Provider>
}

export const useCompany = () => useContext(CompanyContext)
