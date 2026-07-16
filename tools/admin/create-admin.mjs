// Crea (o actualiza) la cuenta de administradora del panel y le asigna el
// custom claim `admin=true` (RF-40).
//
// SIN registro público: esta es la ÚNICA forma de dar de alta una admin. El
// panel jamás crea cuentas ni asigna roles.
//
// Uso:
//   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
//   node create-admin.mjs --email admin@ejemplo.org [--password 'contraseña']
//
//   --password es opcional: si se omite en una cuenta NUEVA, se genera una
//   temporal y se imprime una sola vez (cámbiala al primer ingreso). En una
//   cuenta EXISTENTE, sin --password no se toca la contraseña; con --password
//   se restablece.
//
// La llave de servicio NUNCA debe estar dentro del repositorio (misma política
// que tools/seed): ruta canónica ~/.config/aurora-seed/, vetada por .gitignore,
// y este script aborta (B-1) si la ruta cae dentro del repo.
//
// Idempotente: reejecutar sobre la misma cuenta solo reafirma el claim admin.

import { existsSync, realpathSync } from 'node:fs'
import { dirname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { randomBytes } from 'node:crypto'
import { applicationDefault, initializeApp } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = realpathSync(resolve(here, '..', '..'))

// ---- Argumentos ----
function argValue(flag) {
  const i = process.argv.indexOf(flag)
  return i !== -1 && i + 1 < process.argv.length ? process.argv[i + 1] : undefined
}
const email = argValue('--email')
let password = argValue('--password')

if (!email || !email.includes('@')) {
  console.error('Falta --email <correo> válido.')
  process.exit(1)
}
if (password !== undefined && password.length < 8) {
  console.error('La contraseña debe tener al menos 8 caracteres (RF-07).')
  process.exit(1)
}

// ---- B-1: la llave de servicio jamás dentro del repo ----
const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS
if (!keyPath) {
  console.error(
    'Falta GOOGLE_APPLICATION_CREDENTIALS: ruta a la llave de servicio, que debe vivir FUERA del repositorio.',
  )
  process.exit(1)
}
const resolvedKey = existsSync(keyPath) ? realpathSync(keyPath) : resolve(keyPath)
if (resolvedKey === repoRoot || resolvedKey.startsWith(repoRoot + sep)) {
  console.error(
    `La llave de servicio está DENTRO del repositorio (${resolvedKey}).\n` +
      'Muévela fuera, por ejemplo a ~/.config/aurora-seed/service-account.json, y reintenta.',
  )
  process.exit(1)
}

initializeApp({ credential: applicationDefault() })
const auth = getAuth()

// ---- Crear o localizar la cuenta ----
let user
let generatedPassword
try {
  user = await auth.getUserByEmail(email)
  console.log(`Cuenta existente: ${email} (uid ${user.uid}).`)
  if (password !== undefined) {
    await auth.updateUser(user.uid, { password })
    console.log('Contraseña restablecida.')
  }
} catch (err) {
  if (err.code !== 'auth/user-not-found') {
    console.error(`Error consultando la cuenta: ${err.message}`)
    process.exit(1)
  }
  if (password === undefined) {
    // Contraseña temporal robusta; se imprime una sola vez.
    generatedPassword = randomBytes(12).toString('base64url')
    password = generatedPassword
  }
  user = await auth.createUser({ email, password, emailVerified: false })
  console.log(`Cuenta creada: ${email} (uid ${user.uid}).`)
}

// ---- Asignar el custom claim admin=true (idempotente) ----
// Preserva otros claims que pudiera tener y fija admin=true.
await auth.setCustomUserClaims(user.uid, { ...(user.customClaims ?? {}), admin: true })
console.log('Claim admin=true asignado.')

if (generatedPassword) {
  console.log('\n─────────────────────────────────────────────')
  console.log(`Contraseña temporal (se muestra una sola vez):\n  ${generatedPassword}`)
  console.log('Cámbiala tras el primer ingreso.')
  console.log('─────────────────────────────────────────────')
}

console.log(
  '\nListo. La cuenta debe cerrar sesión y volver a entrar (o refrescar el token) ' +
    'para que el claim admin surta efecto en el panel.',
)
