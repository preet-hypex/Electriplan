import { lazy, Suspense, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useCompany } from '../context/CompanyContext'
import AppLayout from '../components/AppLayout'
import CompanyGate from '../components/CompanyGate'
import { getHouse } from '../api/projects'
import { supabase } from '../lib/supabase'

// The editor is large (canvas, geometry, its own styles): loaded only when opened.
const HouseFloorPlanEditor = lazy(() => import('../floorplan/HouseFloorPlanEditor'))

// Read fresh on every call: supabase-js refreshes the session in the background.
async function getAccessToken() {
  const { data } = await supabase.auth.getSession()
  return data.session?.access_token ?? null
}

/** A house's floor plan in the editor, saved as you go. */
export default function HouseFloorPlan() {
  const { projectId, houseId } = useParams()
  const { company, can } = useCompany()
  const [house, setHouse] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!company) return undefined
    let current = true
    getHouse(houseId).then(h => current && setHouse(h)).catch(e => current && setError(e))
    return () => { current = false }
  }, [company, houseId])

  const back = (
    <Link to={`/projects/${projectId}`} className="btn ghost">
      {house ? `${house.project.reference} · ${house.project.name}` : 'Back to the project'}
    </Link>
  )

  return (
    <AppLayout title={house ? house.name : 'Floor plan'} actions={back} fill>
      <CompanyGate>
        {error && (
          <div className="content">
            <div className="callout bad" role="alert">
              {error.status === 404 ? 'This house does not exist in your company, or it was removed.' : error.message}
            </div>
          </div>
        )}
        {house && (
          <Suspense fallback={<div className="loading">Loading the floor-plan editor…</div>}>
            <HouseFloorPlanEditor
              key={house.id}
              houseId={house.id}
              title={`${house.name} · floor plan`}
              stage={house.stage}
              canEdit={can('floor-plan.edit') && !house.archived}
              getAccessToken={getAccessToken}
            />
          </Suspense>
        )}
      </CompanyGate>
    </AppLayout>
  )
}
