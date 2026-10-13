// Lista las cuentas de Firebase Auth del proyecto, en SOLO LECTURA.
//
// Motivo: `delete-test-account.mjs` inspecciona una cuenta por correo, pero para
// decidir cuál borrar hace falta saber cuáles existen. Este script no escribe
// nada, no lee el contenido de ningún documento y no toca Firestore: solo
// enumera Auth con sus fechas, que es lo que distingue una cuenta desechable de
// la cuenta de prueba de siempre.
//
// Uso:
//   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
//   node listar-cuentas.mjs
//
// La llave de servicio NUNCA debe estar dentro del repositorio: ruta canónica
// ~/.config/aurora-seed/, vetada por .gitignore. Este script aborta si la ruta
// cae dentro del repo, igual que sus hermanos.

import { existsSync, realpathSync } from 'node:fs'
import { dirname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { applicationDefault, initializeApp } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = realpathSync(resolve(here, '..', '..'))

const cred = process.env.GOOGLE_APPLICATION_CREDENTIALS
if (!cred || !existsSync(cred)) {
  console.error('Falta GOOGLE_APPLICATION_CREDENTIALS o la ruta no existe.')
  process.exit(1)
}
if (realpathSync(cred).startsWith(repoRoot + sep)) {
  console.error('La llave de servicio está dentro del repositorio. Abortado.')
  process.exit(1)
}

initializeApp({ credential: applicationDefault() })

const fecha = (s) => (s ? new Date(s).toISOString().replace('T', ' ').slice(0, 19) : '—')

let pageToken
let total = 0
const filas = []
do {
  const page = await getAuth().listUsers(1000, pageToken)
  for (const u of page.users) {
    total++
    filas.push({
      correo: u.email ?? '(sin correo)',
      uid: u.uid,
      creada: fecha(u.metadata.creationTime),
      ultimoAcceso: fecha(u.metadata.lastSignInTime),
      verificado: u.emailVerified
    })
  }
  pageToken = page.pageToken
} while (pageToken)

filas.sort((a, b) => a.creada.localeCompare(b.creada))
console.log(`Cuentas en Firebase Auth: ${total}\n`)
for (const f of filas) {
  console.log(`  ${f.correo}`)
  console.log(`     uid ${f.uid}`)
  console.log(`     creada ${f.creada}   ·   último acceso ${f.ultimoAcceso}`)
}
