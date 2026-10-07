import { useEffect } from 'react'
import { useEditor } from '../state/store'

/** True when the user is typing into a form control, so shortcuts stand down.
 *
 * A checkbox, radio or slider is not typing, and treating it as such is worse
 * than useless: clicking one leaves it focused, and every single-key shortcut
 * then silently does nothing until you click elsewhere.
 */
const NOT_TEXT_ENTRY = new Set(['checkbox', 'radio', 'range', 'button', 'submit', 'reset'])

function isTextEntry(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  if (!el) return false
  if (el.isContentEditable) return true
  const tag = el.tagName
  if (tag === 'TEXTAREA' || tag === 'SELECT') return true
  if (tag !== 'INPUT') return false
  return !NOT_TEXT_ENTRY.has((el as HTMLInputElement).type)
}

export function useKeyboardShortcuts(enabled = true) {
  useEffect(() => {
    if (!enabled) return

    const onKeyDown = (e: KeyboardEvent) => {
      const store = useEditor.getState()
      const mod = e.metaKey || e.ctrlKey

      if (mod && e.key.toLowerCase() === 'z') {
        e.preventDefault()
        if (e.shiftKey) store.redo()
        else store.undo()
        return
      }
      if (mod && e.key.toLowerCase() === 'y') {
        e.preventDefault()
        store.redo()
        return
      }

      if (isTextEntry(e.target)) return

      if (e.key === 'Delete' || e.key === 'Backspace') {
        if (store.selection) {
          e.preventDefault()
          store.deleteSelection()
        }
        return
      }
      if (e.key === 'Escape') {
        store.cancelInteraction()
        store.setDraftWall(null)
        store.setTool('select')
        return
      }
      switch (e.key.toLowerCase()) {
        case 'v':
          store.setTool('select')
          break
        case 'w':
          store.setTool('wall')
          break
        case 'r':
          store.setTool('room')
          break
        case 'd':
          store.setTool('door')
          break
        case 'n':
          store.setTool('window')
          break
        case 't':
          store.toggleText()
          break
        default:
          break
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [enabled])
}
