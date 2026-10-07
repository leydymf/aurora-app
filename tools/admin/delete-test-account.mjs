// Inspecciona y, con --delete, elimina una cuenta de prueba de Firebase Auth
// junto con todos sus datos en Firestore bajo `users/{uid}` (P-30).
//
// Motivo: eliminar un usuario de Auth NO borra sus documentos de Firestore,
// así que primero se purga `users/{uid}` (recursivo, cubre todas las
// subcolecciones: entries, media, mediaparts, meta, outcomes) y después se
// elimina la cuenta. Nunca se lee ni se imprime el contenido de los documentos:
// solo se cuentan.
//
// Uso:
//   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
//   node delete-test-account.mjs --email cuenta@ejemplo.org            (solo inspección)
//   node delete-test-account.mjs --email cuenta@ejemplo.org --delete   (purga + elimina)
//
// Sin --delete el script es de solo lectura: muestra uid, fechas y conteos.
// Con --delete purga Firestore, elimina la cuenta y verifica que ya no exista.
//
// La llave de servicio NUNCA debe estar dentro del repositorio (misma política
// que tools/seed y create-admin.mjs): ruta canónica ~/.config/aurora-seed/,
// vetada por .gitignore, y este script aborta (B-1) si la ruta cae dentro del
// repo.

import { existsSync, realpathSync } from 'node:fs'
import { dirname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { applicationDefault, initializeApp } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { FieldPath, getFirestore } from 'firebase-admin/firestore'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = realpathSync(resolve(here, '..', '..'))

// Argumentos de línea de comandos.
function argValue(flag) {
  const i = process.argv.indexOf(flag)
  return i !== -1 && i + 1 < process.argv.length ? process.argv[i + 1] : undefined
}
const email = argValue('--email')
const shouldDelete = process.argv.includes('--delete')

if (!email || !email.includes('@')) {
  console.error('Falta --email <correo> válido.')
  process.exit(1)
}

// B-1: la llave de servicio jamás dentro del repo.
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
const db = getFirestore()

// Localizar la cuenta en Auth.
let user
try {
  user = await auth.getUserByEmail(email)
} catch (err) {
  if (err.code === 'auth/user-not-found') {
    console.log(`La cuenta ${email} no existe en Auth. Nada que hacer.`)
    process.exit(0)
  }
  console.error(`Error consultando la cuenta: ${err.message}`)
  process.exit(1)
}

console.log(`Cuenta: ${email}`)
console.log(`  uid:                 ${user.uid}`)
console.log(`  creada:              ${user.metadata.creationTime ?? '—'}`)
console.log(`  último inicio:       ${user.metadata.lastSignInTime ?? '—'}`)
console.log(`  último refresco:     ${user.metadata.lastRefreshTime ?? '—'}`)
console.log(`  deshabilitada:       ${user.disabled ? 'sí' : 'no'}`)
console.log(`  claims:              ${JSON.stringify(user.customClaims ?? {})}`)

// Contar documentos bajo users/{uid} sin leer su contenido. La existencia de la
// raíz se consulta con una proyección sin campos (select() vacío) para no
// transferir datos; se enumeran las subcolecciones reales (listCollections) y
// se usa la agregación count().
const users = db.collection('users')
const userDoc = users.doc(user.uid)
const rootQuery = await users.where(FieldPath.documentId(), '==', user.uid).select().get()
const rootExists = !rootQuery.empty
const subcollections = await userDoc.listCollections()
let totalDocs = 0

console.log(`\nFirestore users/${user.uid}:`)
console.log(`  documento raíz:      ${rootExists ? 'existe' : 'no existe'}`)
if (subcollections.length === 0) {
  console.log('  subcolecciones:      ninguna')
} else {
  for (const collection of subcollections) {
    const aggregate = await collection.count().get()
    const count = aggregate.data().count
    totalDocs += count
    console.log(`  ${collection.id.padEnd(20)} ${count} documento(s)`)
  }
}
console.log(`  total:               ${totalDocs} documento(s)`)

if (!shouldDelete) {
  console.log('\nSolo inspección. Para eliminar, reejecuta con --delete.')
  process.exit(0)
}

// Purgar Firestore primero. recursiveDelete elimina el documento raíz (exista o
// no) y todas sus subcolecciones, en lotes gestionados por BulkWriter.
console.log('\nPurgando Firestore users/{uid} (recursivo)...')
await db.recursiveDelete(userDoc)
const remaining = await userDoc.listCollections()
if (remaining.length !== 0) {
  console.error(`Quedaron subcolecciones tras la purga: ${remaining.map((c) => c.id).join(', ')}`)
  process.exit(1)
}
console.log('Firestore purgado: users/{uid} sin subcolecciones.')

// Eliminar la cuenta de Auth.
await auth.deleteUser(user.uid)
console.log('Cuenta eliminada de Auth.')

// Verificación final: getUserByEmail debe fallar con user-not-found.
try {
  await auth.getUserByEmail(email)
  console.error('La cuenta sigue existiendo tras la eliminación.')
  process.exit(1)
} catch (err) {
  if (err.code !== 'auth/user-not-found') {
    console.error(`Error verificando la eliminación: ${err.message}`)
    process.exit(1)
  }
}
console.log(`Verificado: ${email} ya no existe en Auth.`)
