import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Canvas } from './components/canvas/Canvas'
import { DocumentHeader } from './components/DocumentHeader'
import { Ribbon } from './components/Ribbon'
import { Navigator } from './components/Navigator'
import { Inspector } from './components/Inspector'
import { StatusBar } from './components/StatusBar'
import { UploadScreen } from './components/screens/UploadScreen'
import { AnalysisScreen } from './components/screens/AnalysisScreen'
import { CalibrationDialog } from './components/dialogs/CalibrationDialog'
import { useKeyboardShortcuts } from './hooks/useKeyboardShortcuts'
import { useEditor } from './state/store'
import { sampleFloorPlan } from './model/sample'
import { analyse, health, setAccessTokenProvider } from './api/client'
import type { FloorPlan } from './model/types'
import { parseFloorPlanJson } from './model/serialise'
import './floorplan.css'

type Screen = 'upload' | 'analysing' | 'editor'

/**
 * The floor-plan editor: upload → analysis → editor. It fills whatever box it is
 * given (the app shell's content area), and everything it renders sits inside
 * .fp-root, the scope its styles (floorplan.css) apply to.
 */
export default function FloorPlanApp({
  getAccessToken,
}: {
  /** The signed-in user's access token, sent with every call to the analyser. */
  getAccessToken: () => Promise<string | null>
}) {
  setAccessTokenProvider(getAccessToken)
  return (
    <div className="fp-root relative h-full">
      <FloorPlanScreens />
    </div>
  )
}

/** The plan lives in the store, so it outlives a visit to another page; reloading or closing the tab does not. */
function useWarnBeforeUnloadWhenDirty() {
  const dirty = useEditor((s) => s.dirty)
  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])
}

function hasContent(plan: FloorPlan): boolean {
  return plan.walls.length > 0 || plan.rooms.length > 0
}

function FloorPlanScreens() {
  // Coming back to this page with a plan already open goes straight to it.
  const [screen, setScreen] = useState<Screen>(() =>
    hasContent(useEditor.getState().plan) ? 'editor' : 'upload',
  )
  const [analysisState, setAnalysisState] = useState<'running' | 'done' | 'error'>('running')
  const [result, setResult] = useState<FloorPlan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [apiReachable, setApiReachable] = useState<boolean | null>(null)
  const [calibrating, setCalibrating] = useState(false)

  const loadPlan = useEditor((s) => s.loadPlan)
  useKeyboardShortcuts(screen === 'editor' && !calibrating)
  useWarnBeforeUnloadWhenDirty()

  useEffect(() => {
    void health().then(setApiReachable)
  }, [])

  const runAnalysis = useCallback(
    async (f: File) => {
      setFile(f)
      setScreen('analysing')
      setAnalysisState('running')
      setError(null)
      setResult(null)
      try {
        const plan = await analyse(f)
        setResult(plan)
        setAnalysisState('done')
      } catch (e) {
        setError((e as Error).message || 'The analysis service could not be reached.')
        setAnalysisState('error')
      }
    },
    [],
  )

  const openSample = () => {
    loadPlan(sampleFloorPlan(), 'Sample plan')
    setScreen('editor')
  }

  const openJson = async (f: File) => {
    loadPlan(parseFloorPlanJson(await f.text()), f.name.replace(/\.json$/i, ''))
    setScreen('editor')
  }

  /** "Manually trace walls" — start from an empty plan and draw. */
  const startManual = () => {
    loadPlan({
      version: 1,
      units: 'mm',
      walls: [],
      rooms: [],
      doors: [],
      windows: [],
      openings: [],
      labels: [],
      dimensions: [],
    })
    useEditor.getState().setTool('wall')
    setScreen('editor')
  }

  if (screen === 'upload') {
    return (
      <UploadScreen
        onFile={runAnalysis}
        onUseSample={openSample}
        onOpenJson={openJson}
        apiReachable={apiReachable}
      />
    )
  }

  if (screen === 'analysing') {
    return (
      <AnalysisScreen
        state={analysisState}
        plan={result}
        error={error}
        onOpen={() => {
          if (result) loadPlan(result, file?.name.replace(/\.[^.]+$/, ''))
          setScreen('editor')
        }}
        onRetry={() => file && void runAnalysis(file)}
        onManual={startManual}
        onBack={() => setScreen('upload')}
      />
    )
  }

  return (
    <Editor
      onNewAnalysis={() => setScreen('upload')}
      onCalibrate={() => setCalibrating(true)}
      calibrating={calibrating}
      onCloseCalibration={() => setCalibrating(false)}
    />
  )
}

function Editor({
  onNewAnalysis,
  onCalibrate,
  calibrating,
  onCloseCalibration,
}: {
  onNewAnalysis: () => void
  onCalibrate: () => void
  calibrating: boolean
  onCloseCalibration: () => void
}) {
  const canvasWrap = useRef<HTMLDivElement>(null)
  const [canvasSize, setCanvasSize] = useState({ width: 0, height: 0 })

  useLayoutEffect(() => {
    const el = canvasWrap.current
    if (!el) return
    const ro = new ResizeObserver(([entry]) => {
      setCanvasSize({ width: entry.contentRect.width, height: entry.contentRect.height })
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  return (
    <div className="flex h-full w-full flex-col bg-slate-50">
      <DocumentHeader onNewAnalysis={onNewAnalysis} onCalibrate={onCalibrate} />
      <Ribbon canvasSize={canvasSize} />
      <div className="flex min-h-0 flex-1">
        <Navigator />
        <main ref={canvasWrap} className="min-w-0 flex-1">
          <Canvas />
        </main>
        <Inspector />
      </div>
      <StatusBar />
      {calibrating && <CalibrationDialog onClose={onCloseCalibration} />}
    </div>
  )
}
