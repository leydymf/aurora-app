// Siembra del contenido administrable en Firestore (RF-21, RF-22, RF-23).
//
// Uso:
//   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
//   node seed.mjs [--borrador]
//
// La llave de servicio NUNCA debe estar dentro del repositorio (ver README.md).
// El upsert es idempotente (set con merge): editar los JSON de content/ y
// re-ejecutar. OJO: no borra documentos retirados de los JSON — eliminarlos
// a mano en la consola de Firebase si un contenido se retira.
//
// Compuertas de seguridad (auditoría D4):
//  - M-2: los documentos con texto "[POR CONFIRMAR]" se OMITEN por defecto;
//    solo se siembran con --borrador y únicamente en un entorno de prueba.
//  - M-1: exige confirmar que firestore.rules ya está publicado en la consola.
//  - B-1: aborta si la llave de servicio está dentro del repositorio.

import { existsSync, readFileSync, readdirSync, realpathSync } from "node:fs";
import { dirname, join, resolve, sep } from "node:path";
import { createInterface } from "node:readline/promises";
import { fileURLToPath } from "node:url";
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

const here = dirname(fileURLToPath(import.meta.url));
const contentDir = join(here, "content");
const repoRoot = realpathSync(resolve(here, "..", ".."));
const seedDrafts = process.argv.includes("--borrador");
// Por prefijo y sin distinguir mayúsculas: cubre variantes como
// "[POR CONFIRMAR dirección]" (auditoría D4 M-4).
const DRAFT_MARKER = /\[POR CONFIRMAR/i;

const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
if (!keyPath) {
  console.error(
    "Falta GOOGLE_APPLICATION_CREDENTIALS: ruta a la llave de servicio, que debe vivir FUERA del repositorio."
  );
  process.exit(1);
}
// B-1: tercera barrera además de la ruta canónica y del .gitignore.
// realpath resuelve symlinks para que un enlace no esconda la ubicación real.
const resolvedKey = existsSync(keyPath) ? realpathSync(keyPath) : resolve(keyPath);
if (resolvedKey === repoRoot || resolvedKey.startsWith(repoRoot + sep)) {
  console.error(
    `La llave de servicio está DENTRO del repositorio (${resolvedKey}).\n` +
      "Muévela fuera, por ejemplo a ~/.config/aurora-seed/service-account.json, y reintenta."
  );
  process.exit(1);
}

// M-1: en modo de prueba de Firestore cualquiera puede leer Y escribir el
// contenido; publicar las reglas es requisito previo a la primera siembra.
if (process.env.AURORA_RULES_PUBLISHED !== "1") {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const answer = (
    await rl.question("¿Ya publicaste firestore.rules en la consola de Firebase (Firestore → Reglas)? (s/N) ")
  )
    .trim()
    .toLowerCase();
  rl.close();
  if (answer !== "s" && answer !== "si" && answer !== "sí") {
    console.error("Publica primero las reglas y vuelve a ejecutar. (O exporta AURORA_RULES_PUBLISHED=1 si ya lo hiciste.)");
    process.exit(1);
  }
}

initializeApp({ credential: applicationDefault() });
const db = getFirestore();

const files = readdirSync(contentDir).filter((f) => f.endsWith(".json")).sort();
if (files.length === 0) {
  console.error(`No hay archivos de contenido en ${contentDir}`);
  process.exit(1);
}

for (const file of files) {
  const { collection, documents } = JSON.parse(readFileSync(join(contentDir, file), "utf8"));
  if (!collection || !Array.isArray(documents)) {
    console.error(`${file}: se esperaban los campos "collection" y "documents"`);
    process.exit(1);
  }
  const ready = [];
  const skippedIds = [];
  for (const doc of documents) {
    if (!doc.id || doc.schemaVersion == null) {
      console.error(`${file}: todo documento necesita "id" y "schemaVersion"`);
      process.exit(1);
    }
    // M-2: un teléfono o dirección sin verificar es daño real para una usuaria.
    if (!seedDrafts && DRAFT_MARKER.test(JSON.stringify(doc))) {
      skippedIds.push(doc.id);
      continue;
    }
    ready.push(doc);
  }
  if (ready.length > 0) {
    const batch = db.batch();
    for (const { id, ...fields } of ready) {
      batch.set(db.collection(collection).doc(id), fields, { merge: true });
    }
    await batch.commit();
  }
  const skippedNote = skippedIds.length > 0
    ? ` — omitidos por marcador sin confirmar: ${skippedIds.join(", ")} (usa --borrador SOLO en un entorno de prueba)`
    : "";
  console.log(`${collection}: ${ready.length} documentos sembrados desde ${file}${skippedNote}`);
}

console.log("Siembra completa.");
