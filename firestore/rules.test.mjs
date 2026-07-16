// Pruebas de firestore.rules (RNF-06, RF-37; P-04).
//
// Requieren el emulador de Firestore. NO corren en el build de Gradle ni en CI de
// esta fase (no hay Firebase CLI instalado): son la CP manual CP-37 descrita en
// firestore/README.md. Ejecutar con:
//
//   firebase emulators:exec --only firestore "node --test firestore/rules.test.mjs"
//
// Verifican los dos mundos: contenido de solo lectura autenticada y datos de la
// usuaria accesibles solo por su propia cuenta (aislamiento por uid, RNF-06).
import { readFileSync } from 'node:fs'
import { after, before, beforeEach, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, deleteDoc } from 'firebase/firestore'

let env

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'aurora-rules-test',
    firestore: { rules: readFileSync('firestore.rules', 'utf8') }
  })
})

beforeEach(() => env.clearFirestore())
after(() => env.cleanup())

const ana = () => env.authenticatedContext('ana').firestore()
const eva = () => env.authenticatedContext('eva').firestore()
const anon = () => env.unauthenticatedContext().firestore()
// Administradora del panel: token con el custom claim admin==true (RF-40).
const admin = () => env.authenticatedContext('admin', { admin: true }).firestore()

// Siembra contenido saltándose las reglas (como hace la siembra admin).
async function seedContent() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'topics/t01'), { title: 'x' })
  })
}

test('anónima no puede leer el contenido', async () => {
  await seedContent()
  await assertFails(getDoc(doc(anon(), 'topics/t01')))
})

test('autenticada lee el contenido pero no lo escribe', async () => {
  await seedContent()
  await assertSucceeds(getDoc(doc(ana(), 'topics/t01')))
  await assertFails(setDoc(doc(ana(), 'topics/t01'), { title: 'hack' }))
})

test('la administradora (claim admin) sí escribe el contenido (RF-40/41-44)', async () => {
  // Todas las colecciones de contenido admiten escritura con el claim admin.
  await assertSucceeds(setDoc(doc(admin(), 'topics/t01'), { title: 'x', schemaVersion: 1 }))
  await assertSucceeds(setDoc(doc(admin(), 'routes/r01'), { title: 'x', schemaVersion: 1 }))
  await assertSucceeds(setDoc(doc(admin(), 'agencies/a01'), { name: 'x', schemaVersion: 1 }))
  await assertSucceeds(setDoc(doc(admin(), 'lines/l01'), { name: 'x', schemaVersion: 1 }))
  await assertSucceeds(setDoc(doc(admin(), 'forms/f01'), { version: 1, schemaVersion: 1 }))
})

test('la administradora NO puede leer ni escribir datos de usuarias (RNF-06)', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/ana/entries/7'), { payload: 'blob' })
  })
  // El claim admin no otorga ningún acceso sobre /users: la condición es uid.
  await assertFails(getDoc(doc(admin(), 'users/ana/entries/7')))
  await assertFails(setDoc(doc(admin(), 'users/ana/entries/7'), { payload: 'hack' }))
})

test('la usuaria lee y escribe sus propios registros', async () => {
  const ref = doc(ana(), 'users/ana/entries/7')
  await assertSucceeds(setDoc(ref, { payload: 'blob', iv: 'n', schemaVersion: 1 }))
  await assertSucceeds(getDoc(ref))
  await assertSucceeds(deleteDoc(ref))
})

test('nadie puede leer los registros de otra usuaria (RNF-06)', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/ana/entries/7'), { payload: 'blob' })
  })
  await assertFails(getDoc(doc(eva(), 'users/ana/entries/7')))
  await assertFails(setDoc(doc(eva(), 'users/ana/entries/7'), { payload: 'hack' }))
  await assertFails(getDoc(doc(anon(), 'users/ana/entries/7')))
})

test('todo lo demás está negado', async () => {
  await assertFails(getDoc(doc(ana(), 'secretos/x')))
  await assertFails(setDoc(doc(ana(), 'secretos/x'), { a: 1 }))
})
