import { lazy, Suspense } from 'react'
import AppLayout from '../components/AppLayout'

// The floor-plan editor is large (canvas, geometry, its own styles), so it loads
// only when this page is opened.
const FloorPlanApp = lazy(() => import('../floorplan/FloorPlanApp'))

/** The floor-plan analyser and editor, filling the app shell's content area. */
export default function FloorPlan() {
  return (
    <AppLayout title="Floor plans" fill>
      <Suspense fallback={<div className="loading">Loading the floor-plan editor…</div>}>
        <FloorPlanApp />
      </Suspense>
    </AppLayout>
  )
}
