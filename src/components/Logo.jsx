import brand, { monogram } from '../lib/brand'

export default function Logo({ large = false }) {
  return (
    <div className={`brand${large ? ' lg' : ''}`} aria-label={brand.name}>
      <div className="mark" aria-hidden="true">{monogram(brand.name)}</div>
      <div className="word">{brand.name}<span>.</span></div>
    </div>
  )
}
