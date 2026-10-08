import { useEffect, useId, useRef, useState } from 'react'
import { findAddresses } from '../../api/projects'

const MIN_CHARACTERS = 3

/**
 * Type part of an address, pick a suggestion, and the site fields are filled
 * in (street, suburb, state, postcode). The fields stay editable: new estates
 * and some house numbers are not in OpenStreetMap yet.
 *
 * A combobox (ARIA 1.2): arrow keys move through suggestions, Enter picks,
 * Escape closes.
 */
export default function AddressSearch({ onPick }) {
  const [text, setText] = useState('')
  const [suggestions, setSuggestions] = useState([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [note, setNote] = useState(null)
  const listId = useId()
  const latest = useRef(0)

  // Ask once typing has settled; only the newest answer counts.
  useEffect(() => {
    const typed = text.trim()
    if (typed.length < MIN_CHARACTERS) {
      setSuggestions([])
      setNote(null)
      return undefined
    }
    const ask = ++latest.current
    const timer = setTimeout(() => {
      findAddresses(typed)
        .then(found => {
          if (ask !== latest.current) return
          setSuggestions(found)
          setActive(-1)
          setNote(found.length === 0 ? 'No matching address found. Type it into the fields below.' : null)
        })
        .catch(e => {
          if (ask !== latest.current) return
          setSuggestions([])
          setNote(e.message)
        })
    }, 300)
    return () => clearTimeout(timer)
  }, [text])

  const pick = suggestion => {
    onPick(suggestion)
    setText(suggestion.label)
    setOpen(false)
    setSuggestions([])
    setNote(suggestion.street && /^\d/.test(suggestion.street) ? 'Check the house number.' : 'Add the house number if there is one.')
  }

  const onKeyDown = e => {
    if (!open || suggestions.length === 0) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive(i => (i + 1) % suggestions.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive(i => (i <= 0 ? suggestions.length - 1 : i - 1))
    } else if (e.key === 'Enter' && active >= 0) {
      e.preventDefault()
      pick(suggestions[active])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  const showList = open && suggestions.length > 0
  return (
    <div className="fld address-search">
      <label htmlFor={`${listId}-input`}>Find the address</label>
      <input
        id={`${listId}-input`}
        className="in"
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
        placeholder="Start typing a street address"
        value={text}
        autoComplete="off"
        onChange={e => { setText(e.target.value); setOpen(true) }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={onKeyDown}
      />
      {showList && (
        <ul className="suggestions" role="listbox" id={listId} aria-label="Matching addresses">
          {suggestions.map((s, i) => (
            <li key={s.label} id={`${listId}-${i}`} role="option" aria-selected={i === active}
              className={i === active ? 'active' : undefined}
              onMouseDown={e => { e.preventDefault(); pick(s) }}>
              {s.label}
            </li>
          ))}
        </ul>
      )}
      {note
        ? <span className="fld-hint">{note}</span>
        : <span className="fld-hint">Addresses from OpenStreetMap. Or fill in the fields below.</span>}
    </div>
  )
}
