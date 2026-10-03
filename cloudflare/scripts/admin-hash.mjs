import { randomBytes } from 'node:crypto'
import { passwordHash, ITERATIONS } from '../src/auth.js'

if (!process.stdin.isTTY) throw new Error('Run in an interactive terminal; passwords must not be command arguments.')
async function hiddenInput(prompt) {
  process.stdout.write(prompt)
  process.stdin.setRawMode(true)
  process.stdin.resume()
  process.stdin.setEncoding('utf8')
  return new Promise((resolve, reject) => {
    let value = ''
    const finish = () => { process.stdin.setRawMode(false); process.stdin.pause(); process.stdin.off('data', onData); process.stdout.write('\n') }
    const onData = chunk => {
      for (const char of chunk) {
        if (char === '\u0003') { finish(); reject(new Error('Cancelled')); return }
        if (char === '\r' || char === '\n') { finish(); resolve(value); return }
        if (char === '\u007f' || char === '\b') value = value.slice(0, -1)
        else if (char >= ' ') value += char
      }
    }
    process.stdin.on('data', onData)
  })
}
const password = await hiddenInput('Administrator password (hidden, 12+ characters): ')
if (password.length < 12 || password.length > 1024 || !password.trim()) throw new Error('Password must be 12-1024 characters')
if (password !== await hiddenInput('Confirm password: ')) throw new Error('Passwords do not match')
const salt = randomBytes(16).toString('hex')
console.log(`\nBLOG_ADMIN_PASSWORD_HASH=${ITERATIONS}:${salt}:${await passwordHash(password, salt)}`)
console.log(`BLOG_JWT_SECRET=${randomBytes(48).toString('hex')}`)
console.log('\nTreat these values as secrets. Do not commit them or send them in chat.')
