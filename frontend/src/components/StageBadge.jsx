import { STAGE_LABELS, stageTone } from '../api/projects'

/** Where a house is, in words, coloured by how far along it is. */
export default function StageBadge({ stage, count }) {
  return (
    <span className={`stage ${stageTone(stage)}`}>
      {count !== undefined && <b>{count}</b>}
      {STAGE_LABELS[stage] ?? stage}
    </span>
  )
}
