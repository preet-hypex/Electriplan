import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import AppLayout, { displayName } from '../components/AppLayout'
import Icon from '../components/Icon'

// The workflow the product is built around. Status is the truth about what can
// be used today, not a promise.
const STEPS = [
  {
    icon: 'plan', title: 'Floor plan', status: 'available', to: '/floor-plan',
    text: 'Upload a plan image. Walls, rooms, doors, windows and room names are found and laid out to scale.',
  },
  {
    icon: 'bolt', title: 'Electrical layout', status: 'next',
    text: 'Lights, switches, power points, wet-area zones and circuits, laid out to AS/NZS 3000.',
  },
  {
    icon: 'review', title: 'Electrician review', status: 'planned',
    text: 'A licensed electrician checks, adjusts and signs off the design.',
  },
  {
    icon: 'quote', title: 'Quote', status: 'planned',
    text: 'A bill of materials with cable lengths, ready to price and quote.',
  },
]

const STATUS = { available: 'Available', next: 'Coming next', planned: 'Planned' }

function greeting(date = new Date()) {
  const h = date.getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

/** The landing page after signing in. */
export default function Home() {
  const { user } = useAuth()

  return (
    <AppLayout
      title="Home"
      actions={<Link to="/floor-plan" className="btn pri"><Icon name="upload" size={16} />New floor plan</Link>}
    >
      <section className="hero">
        <div>
          <p className="eyebrow">{greeting()}, {displayName(user)}</p>
          <h2>From a floor plan to an electrician-ready design.</h2>
          <p className="lede">Start with a floor-plan image. We turn it into an accurate, editable plan — the base
            for the electrical layout, review and quote that follow.</p>
        </div>
        <div className="hero-actions">
          <Link to="/floor-plan" className="btn pri lg"><Icon name="upload" size={16} />Upload a floor plan</Link>
          <span className="hint">JPG or PNG, up to 25 MB</span>
        </div>
      </section>

      <section aria-labelledby="workflow">
        <div className="section-head">
          <h3 id="workflow">Your workflow</h3>
          <span className="meta">Four steps from plan to quote</span>
        </div>
        <ol className="steps">
          {STEPS.map((step, i) => {
            const body = (
              <>
                <div className="step-top">
                  <span className="step-icon"><Icon name={step.icon} /></span>
                  <span className={`badge ${step.status}`}>{STATUS[step.status]}</span>
                </div>
                <div className="step-num">Step {i + 1}</div>
                <h4>{step.title}</h4>
                <p>{step.text}</p>
                {step.to && <span className="step-go">Open <Icon name="arrow" size={14} /></span>}
              </>
            )
            return (
              <li key={step.title}>
                {step.to
                  ? <Link to={step.to} className="step live">{body}</Link>
                  : <div className="step" aria-disabled="true">{body}</div>}
              </li>
            )
          })}
        </ol>
      </section>

      <div className="split">
        <section className="panel" aria-labelledby="recent">
          <div className="pnh"><h3 id="recent">Recent projects</h3></div>
          <div className="empty">
            <span className="empty-icon"><Icon name="folder" size={22} /></span>
            <h4>No saved projects yet</h4>
            <p>For now, plans are saved as JSON files from the editor and reopened from the upload screen.
              Projects saved to your account arrive with the electrical layout.</p>
            <Link to="/floor-plan" className="btn">Open the floor-plan editor</Link>
          </div>
        </section>

        <section className="panel" aria-labelledby="start">
          <div className="pnh"><h3 id="start">Getting started</h3></div>
          <ol className="checklist">
            <li className="done">
              <span className="tick"><Icon name="check" size={14} /></span>
              <div><b>Sign in</b><p>You’re signed in as {user.email}.</p></div>
            </li>
            <li>
              <span className="tick">2</span>
              <div><b>Upload a floor plan</b><p>Drop a JPG or PNG of a plan, or open the sample plan from the
                upload screen.</p></div>
            </li>
            <li>
              <span className="tick">3</span>
              <div><b>Check the scale</b><p>Use <i>Calibrate scale</i> in the editor with a dimension you
                know, so every length is right.</p></div>
            </li>
            <li>
              <span className="tick">4</span>
              <div><b>Correct and save</b><p>Fix any walls, rooms or openings, then <i>Save JSON</i>.</p></div>
            </li>
          </ol>
        </section>
      </div>
    </AppLayout>
  )
}
