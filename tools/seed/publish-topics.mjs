// Publica SOLO un subconjunto de documentos de la colección `topics` (por
// defecto t05 y t06, validados por la directora en octubre de 2026) sin tocar
// el resto de los temas ni ninguna otra colección.
//
// Uso:
//   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
//   node publish-topics.mjs                  # modo de prueba: muestra qué escribiría y termina
//   node publish-topics.mjs --publicar       # escribe de verdad (pide confirmación)
//   node publish-topics.mjs --ids t05,t06    # otro subconjunto explícito (nunca «todos»)
//
// Requisito previo (ADR 2026-10-03): el APK con BD v5 / payload v3 debe estar
// instalado antes de publicar; el registro de un APK anterior no conocería los
// temas nuevos.
//
// Comparte las barreras de seed.mjs: la llave de servicio nunca dentro del
// repositorio (B-1), reglas publicadas antes de escribir (M-1) y rechazo de
// documentos con "[POR CONFIRMAR" (M-2). El modo de prueba no toca la red.
//
// Escribe el documento vivo Y su borrador del panel (2026-10-06). Antes solo
// tocaba `topics`, así que el borrador que edita el panel se quedaba congelado
// en lo último que se guardó allí: para t05 eso era el texto «Prueba» de una
// corrida E2E de julio, y un clic en «Publicar» habría sobrescrito el contenido
// validado por la directora. Dejando los dos lados iguales, el panel siempre
// parte de lo que la app está mostrando.

import { existsSync, readFileSync, realpathSync } from "node:fs";
import { dirname, join, resolve, sep } from "node:path";
import { createInterface } from "node:readline/promises";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = realpathSync(resolve(here, "..", ".."));
const contentFile = join(here, "content", "topics.json");
const DRAFT_MARKER = /\[POR CONFIRMAR/i;
const DEFAULT_IDS = ["t05", "t06"];

const args = process.argv.slice(2);
const publish = args.includes("--publicar");
const idsIndex = args.indexOf("--ids");
const ids = idsIndex >= 0 ? (args[idsIndex + 1] ?? "").split(",").map((s) => s.trim()).filter(Boolean) : DEFAULT_IDS;
if (ids.length === 0) {
  console.error("--ids necesita una lista explícita, por ejemplo --ids t05,t06");
  process.exit(1);
}

const { collection, documents } = JSON.parse(readFileSync(contentFile, "utf8"));
if (collection !== "topics" || !Array.isArray(documents)) {
  console.error("content/topics.json: se esperaba collection \"topics\" y un arreglo \"documents\"");
  process.exit(1);
}

const selected = [];
for (const id of ids) {
  const doc = documents.find((d) => d.id === id);
  if (!doc) {
    console.error(`No existe el documento ${id} en content/topics.json`);
    process.exit(1);
  }
  if (doc.schemaVersion == null) {
    console.error(`${id}: falta schemaVersion`);
    process.exit(1);
  }
  if (DRAFT_MARKER.test(JSON.stringify(doc))) {
    console.error(`${id}: contiene "[POR CONFIRMAR" y no puede publicarse (compuerta M-2)`);
    process.exit(1);
  }
  selected.push(doc);
}

console.log(`${publish ? "PUBLICACIÓN" : "MODO DE PRUEBA (no escribe nada)"} — colección "${collection}", ${selected.length} documento(s):`);
for (const doc of selected) {
  console.log(`  ${doc.id}: order ${doc.order}, ${doc.icon}/${doc.accent}, schemaVersion ${doc.schemaVersion}, "${doc.title}", ${doc.signals?.length ?? 0} señales, ${doc.examples?.length ?? 0} ejemplos`);
}
console.log("También se iguala el borrador del panel de cada uno, para que no quede más viejo que lo publicado.");
console.log("El resto de topics y las demás colecciones no se tocan.");

if (!publish) {
  console.log("Para escribir de verdad: node publish-topics.mjs --publicar");
  process.exit(0);
}

const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
if (!keyPath) {
  console.error("Falta GOOGLE_APPLICATION_CREDENTIALS: ruta a la llave de servicio, que debe vivir FUERA del repositorio.");
  process.exit(1);
}
const resolvedKey = existsSync(keyPath) ? realpathSync(keyPath) : resolve(keyPath);
if (resolvedKey === repoRoot || resolvedKey.startsWith(repoRoot + sep)) {
  console.error("La llave de servicio está DENTRO del repositorio. Muévela fuera (p. ej. ~/.config/aurora-seed/) y reintenta.");
  process.exit(1);
}

const rl = createInterface({ input: process.stdin, output: process.stdout });
if (process.env.AURORA_RULES_PUBLISHED !== "1") {
  const rules = (await rl.question("¿Ya publicaste firestore.rules en la consola de Firebase? (s/N) ")).trim().toLowerCase();
  if (!["s", "si", "sí"].includes(rules)) {
    rl.close();
    console.error("Publica primero las reglas y vuelve a ejecutar. (O exporta AURORA_RULES_PUBLISHED=1 si ya lo hiciste.)");
    process.exit(1);
  }
}
const confirm = (await rl.question(`¿Está instalado el APK con BD v5 / payload v3 y quieres publicar ${ids.join(", ")} en producción? (s/N) `))
  .trim()
  .toLowerCase();
rl.close();
if (!["s", "si", "sí"].includes(confirm)) {
  console.log("Cancelado: no se escribió nada.");
  process.exit(0);
}

const { applicationDefault, initializeApp } = await import("firebase-admin/app");
const { getFirestore } = await import("firebase-admin/firestore");
initializeApp({ credential: applicationDefault() });
const db = getFirestore();
const { FieldValue } = await import("firebase-admin/firestore");
const batch = db.batch();
for (const { id, ...fields } of selected) {
  // Sin `merge`: el documento vivo queda EXACTAMENTE con los campos de la
  // fuente, que es lo único que hace que el borrador de abajo sea idéntico a lo
  // publicado. Con merge, un campo heredado ausente de la fuente dejaría los dos
  // lados distintos y el panel marcaría el borrador como obsoleto enseguida.
  batch.set(db.collection(collection).doc(id), fields);
  // El borrador del panel queda igual a lo publicado, con el mismo id compuesto
  // que usa admin/src/lib/drafts.ts (`${coleccion}__${docId}`).
  batch.set(db.collection("drafts").doc(`${collection}__${id}`), {
    collection,
    docId: id,
    status: "publicado",
    data: fields,
    updatedAt: FieldValue.serverTimestamp(),
    publishedAt: FieldValue.serverTimestamp(),
  });
}
await batch.commit();
console.log(`Publicados ${selected.map((d) => d.id).join(", ")} en "${collection}" y en sus borradores del panel.`);
