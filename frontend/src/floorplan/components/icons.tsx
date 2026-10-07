/**
 * Line icons for the editor's ribbon, navigator and header, on a 20×20 grid in
 * the current text colour. Same drawing style as the app shell's icons.
 */
const PATHS = {
  select: 'M5 3.5 15 10l-4.5 1-2 4.5L5 3.5Z',
  wall: 'M3 7h14v6H3V7Zm4.5 0v6m5-6v6',
  room: 'M3.5 3.5h13v13h-13v-13Zm0 0 13 13m0-13-13 13',
  door: 'M5 16.5V3.5h7v13M5 16.5h10M12 3.5c2.8.8 4 3.4 4 6.5',
  window: 'M3.5 6.5h13v7h-13v-7Zm6.5 0v7M3.5 10h13',
  opening: 'M3 10h4m6 0h4M7 6.5v7m6-7v7',
  label: 'M4 5.5h12M10 5.5v10M7.5 15.5h5',
  undo: 'M7 5 3.5 8.5 7 12M3.5 8.5H12a4.5 4.5 0 0 1 0 9H9',
  redo: 'M13 5l3.5 3.5L13 12m3.5-3.5H8a4.5 4.5 0 0 0 0 9h3',
  open: 'M2.5 5.5a1 1 0 0 1 1-1h4l1.5 2h7.5a1 1 0 0 1 1 1v7.5a1 1 0 0 1-1 1h-13a1 1 0 0 1-1-1V5.5Z',
  save: 'M4.5 3.5h9l3 3v9a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1Zm2 0v4h6v-4m-6 13v-5h7v5',
  scan: 'M3.5 7V4.5a1 1 0 0 1 1-1H7m6 0h2.5a1 1 0 0 1 1 1V7m0 6v2.5a1 1 0 0 1-1 1H13m-6 0H4.5a1 1 0 0 1-1-1V13M3.5 10h13',
  ruler: 'M2.5 13.5 13.5 2.5l4 4-11 11-4-4Zm3.5-.5 1.5 1.5M8.5 10.5l1.5 1.5M11 8l1.5 1.5',
  zoomIn: 'M9 15.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13Zm4.6-1.9 3.4 3.4M9 6v6m-3-3h6',
  zoomOut: 'M9 15.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13Zm4.6-1.9 3.4 3.4M6 9h6',
  fit: 'M3.5 7.5v-4h4m5 0h4v4m0 5v4h-4m-5 0h-4v-4',
  grid: 'M3.5 3.5h13v13h-13v-13Zm0 4.33h13m-13 4.34h13M7.83 3.5v13m4.34-13v13',
  text: 'M4 5.5V4h12v1.5M10 4v12m-2.5 0h5',
  ortho: 'M4 16V4m0 12h12',
  magnet: 'M5 3.5v6a5 5 0 0 0 10 0v-6h-3.5v6a1.5 1.5 0 0 1-3 0v-6H5Zm0 3h3.5m3 0H15',
  search: 'M8.5 14.5a6 6 0 1 0 0-12 6 6 0 0 0 0 12Zm4.3-1.7 4.2 4.2',
  chevron: 'm7.5 5 5 5-5 5',
  warning: 'M10 3 2.5 16.5h15L10 3Zm0 5v4m0 2.5v.01',
  info: 'M10 17a7 7 0 1 0 0-14 7 7 0 0 0 0 14Zm0-8v4.5m0-7v.01',
  check: 'm4.5 10.5 3.5 3.5 7.5-8',
  keyboard: 'M2.5 5.5h15v9h-15v-9Zm3 3h1m2.5 0h1m2.5 0h1m2.5 0h.5M5.5 11.5h9',
  image: 'M3.5 3.5h13v13h-13v-13Zm0 9.5 4-4 3 3 2-2 3.5 3.5M12.5 7.5v.01',
  pencil: 'M12.5 4.5l3 3-8.5 8.5H4v-3l8.5-8.5Z',
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name, size = 16, className }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      <path d={PATHS[name]} />
    </svg>
  )
}
