/**
 * Tailwind is used by the floor-plan editor only (src/floorplan), which came
 * across from the editor MVP written with it. The rest of the app is styled by
 * src/styles/*.css, so:
 *   - content is limited to src/floorplan, so no utility is generated for a
 *     class name some other page happens to use;
 *   - preflight is off, so Tailwind's global reset never touches the other
 *     pages. src/floorplan/floorplan.css applies the same reset, scoped to the
 *     editor's root element.
 *
 * @type {import('tailwindcss').Config}
 */
// The editor came across written against Tailwind's own slate and blue palettes.
// Rather than rewrite every class, those two palettes are pointed at the app's
// design tokens (src/styles/tokens.css and the generated theme.css), so the
// editor wears the same greys, accent, type and corners as every other page, and
// follows the accent when setup changes it. Because the values are CSS
// variables, Tailwind's opacity modifiers (bg-slate-900/80) do not work on them;
// use an arbitrary color-mix() value instead.
const slate = {
  50: 'var(--sub)',
  100: 'var(--canvas)',
  200: 'var(--line)',
  300: 'var(--line-strong)',
  400: 'var(--faint)',
  500: 'var(--muted)',
  600: 'var(--body)',
  700: 'var(--text)',
  800: 'var(--ink-2)',
  900: 'var(--ink)',
}

const blue = {
  50: 'var(--accent-soft)',
  100: 'var(--accent-t)',
  200: 'var(--accent-b)',
  500: 'var(--accent)',
  600: 'var(--accent)',
  700: 'var(--accent-h)',
}

export default {
  content: ['./src/floorplan/**/*.{ts,tsx}'],
  corePlugins: { preflight: false },
  theme: {
    extend: {
      colors: { slate, blue },
      fontFamily: { sans: 'var(--font)' },
      borderRadius: { DEFAULT: 'var(--r)', lg: '10px' },
      keyframes: {
        // An indeterminate bar: a segment travelling the width of its track.
        // The analysis is one blocking request with no progress to report, so
        // this says "still working" without claiming to know how far along it
        // is. Offsets are multiples of the segment's own width.
        indeterminate: {
          '0%': { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(400%)' },
        },
      },
      animation: {
        indeterminate: 'indeterminate 1.1s ease-in-out infinite',
      },
      colors: {
        // Confidence bands used across the editor (see src/floorplan/model/confidence.ts).
        conf: {
          high: '#16a34a',
          medium: '#d97706',
          low: '#dc2626',
        },
      },
    },
  },
  plugins: [],
}
