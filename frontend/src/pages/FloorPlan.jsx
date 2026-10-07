import { lazy, Suspense } from 'react'
import AppLayout from '../components/AppLayout'
import { supabase } from '../lib/supabase'

// The floor-plan editor is large (canvas, geometry, its own styles), so it loads
// only when this page is opened.
const FloorPlanApp = lazy(() => import('../floorplan/FloorPlanApp'))

// Read fresh on every call: supabase-js refreshes the session in the background,
// so a token captured once would expire under a long editing session.
async function getAccessToken() {
  const { data } = await supabase.auth.getSession()
  return data.session?.access_token ?? null
}

/** The floor-plan analyser and editor, filling the app shell's content area. */
export default function FloorPlan() {
  return (
    <AppLayout title="Floor plans" fill>
      <Suspense fallback={<div className="loading">Loading the floor-plan editor…</div>}>
        <FloorPlanApp getAccessToken={getAccessToken} />
      </Suspense>
    </AppLayout>
  )
}
