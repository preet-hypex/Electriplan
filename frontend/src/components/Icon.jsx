// Line icons for the app shell, drawn on a 20×20 grid in the current text colour.
const PATHS = {
  home: 'M3.5 9 10 3.5 16.5 9v7a1 1 0 0 1-1 1h-3.5v-5h-4v5H4.5a1 1 0 0 1-1-1V9Z',
  plan: 'M3.5 3.5h13v13h-13v-13Zm0 6h6m3 0h4m-7-6v4m0 3v6',
  bolt: 'M11 2.5 4.5 11H10l-1 6.5L15.5 9H10l1-6.5Z',
  review: 'M7 3.5h6m-6 0a1 1 0 0 0-1 1v0a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1v0a1 1 0 0 0-1-1M6 4.5H4.5v12h11v-12H14M7.5 11l2 2 3.5-4',
  quote: 'M5 2.5h10v15l-2.5-1.5-2.5 1.5-2.5-1.5L5 17.5v-15Zm3 4h4m-4 3h4m-4 3h2',
  user: 'M10 10a3.25 3.25 0 1 0 0-6.5 3.25 3.25 0 0 0 0 6.5Zm-6 6.5c.6-3 3-4.5 6-4.5s5.4 1.5 6 4.5',
  upload: 'M10 13V3.5m0 0L6.5 7M10 3.5 13.5 7M3.5 12.5v3a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1v-3',
  arrow: 'M4 10h12m0 0-4.5-4.5M16 10l-4.5 4.5',
  check: 'm4.5 10.5 3.5 3.5 7.5-8',
  signout: 'M8 16.5H4.5a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1H8m4.5 10L16 10l-3.5-3.5M16 10H7.5',
  menu: 'M3.5 5.5h13M3.5 10h13M3.5 14.5h13',
  folder: 'M2.5 5.5a1 1 0 0 1 1-1h4l1.5 2h7.5a1 1 0 0 1 1 1v7.5a1 1 0 0 1-1 1h-13a1 1 0 0 1-1-1V5.5Z',
}

export default function Icon({ name, size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" stroke="currentColor"
      strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={PATHS[name]} />
    </svg>
  )
}
