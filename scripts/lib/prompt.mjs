import readline from 'node:readline'
import { Writable } from 'node:stream'

const colour = process.stdout.isTTY && !process.env.NO_COLOR

export const style = {
  bold: s => (colour ? `\x1b[1m${s}\x1b[22m` : s),
  dim: s => (colour ? `\x1b[2m${s}\x1b[22m` : s),
  red: s => (colour ? `\x1b[31m${s}\x1b[39m` : s),
  green: s => (colour ? `\x1b[32m${s}\x1b[39m` : s),
  yellow: s => (colour ? `\x1b[33m${s}\x1b[39m` : s),
  swatch: ({ r, g, b }) => (colour ? `\x1b[48;2;${r};${g};${b}m    \x1b[49m` : '    '),
}

export class InputEnded extends Error {
  constructor() {
    super('input ended before setup finished')
  }
}

export function createPrompter() {
  let muted = false
  const output = new Writable({
    write(chunk, encoding, done) {
      if (!muted) process.stdout.write(chunk, encoding)
      done()
    },
  })
  const rl = readline.createInterface({ input: process.stdin, output, terminal: Boolean(process.stdin.isTTY) })
  const lines = []
  const waiting = []
  let closed = false

  rl.on('line', line => (waiting.length ? waiting.shift()(line) : lines.push(line)))
  rl.on('close', () => {
    closed = true
    while (waiting.length) waiting.shift()(null)
  })
  rl.on('SIGINT', () => {
    process.stdout.write('\n')
    process.exit(130)
  })

  async function nextLine() {
    const line = lines.length ? lines.shift() : closed ? null : await new Promise(resolve => waiting.push(resolve))
    if (line === null) {
      process.stdout.write('\n')
      throw new InputEnded()
    }
    return line
  }

  function show(text) {
    if (closed) process.stdout.write(text)
    else {
      rl.setPrompt(text)
      rl.prompt()
    }
  }

  async function ask(question, fallback = '') {
    const hint = fallback ? ` ${style.dim(`[${fallback}]`)}` : ''
    show(`${question}${hint}: `)
    const answer = (await nextLine()).trim()
    if (!process.stdin.isTTY) process.stdout.write('\n')
    return answer || fallback
  }

  async function askHidden(question) {
    process.stdout.write(`${question}: `)
    if (!closed) rl.setPrompt('')
    muted = true
    try {
      return (await nextLine()).trim()
    } finally {
      muted = false
      process.stdout.write('\n')
    }
  }

  async function confirm(question, yes = true) {
    const answer = (await ask(`${question} ${style.dim(yes ? '(Y/n)' : '(y/N)')}`)).toLowerCase()
    if (!answer) return yes
    return answer.startsWith('y')
  }

  return { ask, askHidden, confirm, close: () => rl.close() }
}
