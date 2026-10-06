// Retira borradores del panel que quedaron OBSOLETOS: su contenido ya no
// coincide con el documento vivo que la app está mostrando.
//
// Por qué existe (2026-10-06): el panel guarda lo que se edita en `drafts/
// {coleccion}__{docId}`, pero la siembra y `publicar-tipos` escriben directo en
// la colección viva. Cuando se publica por esa vía, el borrador se queda
// congelado en lo último que se tocó en el panel. En producción eso dejó
// borradores de julio de 2026 con texto de pruebas («Prueba» en t05) y hasta
// con OTRA entidad (a03) o el cuestionario viejo de seis preguntas (f01):
// un clic en «Publicar» habría sobrescrito contenido validado.
//
// Desde esa misma fecha el panel ya no muestra un borrador más viejo que lo
// publicado, así que esto no repara un fallo en curso: deja la base limpia.
//
// Uso:
//   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
//   node limpiar-borradores.mjs                      # modo de prueba: solo informa
//   node limpiar-borradores.mjs --ids topics__t05    # acota a unos ids concretos
//   node limpiar-borradores.mjs --borrar             # borra de verdad (pide confirmación)
//
// El modo de prueba es el DEFAULT y lista, uno por uno, qué dice el borrador y
// qué dice lo publicado, para decidir con el contenido a la vista.
//
// Mismas barreras que seed.mjs: la llave de servicio nunca dentro del
// repositorio (B-1) y confirmación explícita antes de escribir.
import { existsSync, realpathSync } from "node:fs";
import { createInterface } from "node:readline/promises";
import { dirname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = realpathSync(resolve(here, "..", ".."));

const args = process.argv.slice(2);
const erase = args.includes("--borrar");
const idsIndex = args.indexOf("--ids");
const onlyIds =
  idsIndex >= 0
    ? (args[idsIndex + 1] ?? "")
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
    : null;

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

// Lista blanca: la herramienta corre con llave de servicio y el nombre de la
// colección viene DEL DATO. Sin esta lista, un borrador con `collection:"users"`
// la haría leer e imprimir documentos de cuentas (auditoría 2026-10-06).
const COLECCIONES = new Set(["topics", "routes", "agencies", "lines", "forms"]);

const { applicationDefault, initializeApp } = await import("firebase-admin/app");
const { getFirestore } = await import("firebase-admin/firestore");
initializeApp({ credential: applicationDefault() });
const db = getFirestore();

/** Comparación estable: el orden de las claves no cuenta como diferencia. */
const stable = (value) =>
  JSON.stringify(value, (_key, raw) => {
    if (raw === null || typeof raw !== "object" || Array.isArray(raw)) return raw;
    return Object.keys(raw)
      .sort()
      .reduce((acc, key) => {
        acc[key] = raw[key];
        return acc;
      }, {});
  });

const resumen = (value, max = 90) => {
  if (value === undefined) return "(sin el campo)";
  const text = typeof value === "string" ? value : JSON.stringify(value);
  return text.length > max ? `${text.slice(0, max)}…` : text;
};

const fecha = (timestamp) => (timestamp?.toDate ? timestamp.toDate().toISOString() : "(sin fecha)");

const snapshot = await db.collection("drafts").get();
const obsoletos = [];

for (const document of snapshot.docs) {
  if (onlyIds && !onlyIds.includes(document.id)) continue;
  const draft = document.data();
  const collection = draft.collection;
  const docId = draft.docId;
  if (typeof collection !== "string" || typeof docId !== "string") {
    console.log(`· ${document.id}: borrador con forma inesperada, se omite.`);
    continue;
  }
  if (!COLECCIONES.has(collection)) {
    console.log(`· ${document.id}: apunta a la colección "${collection}", que no es de contenido. NO se toca.`);
    continue;
  }
  const live = await db.collection(collection).doc(docId).get();
  if (!live.exists) {
    console.log(`· ${document.id}: no hay documento publicado — es un recurso nuevo sin publicar, NO se toca.`);
    continue;
  }
  if (draft.status !== "publicado") {
    console.log(`· ${document.id}: borrador EN EDICIÓN (trabajo sin publicar), NO se toca.`);
    continue;
  }
  if (stable(draft.data) === stable(live.data())) {
    console.log(`· ${document.id}: al día con lo publicado, NO se toca.`);
    continue;
  }

  obsoletos.push({ id: document.id, collection, docId });
  const draftData = draft.data ?? {};
  const liveData = live.data() ?? {};
  const campos = [...new Set([...Object.keys(draftData), ...Object.keys(liveData)])].sort();
  console.log(`\n· ${document.id} — OBSOLETO (guardado ${fecha(draft.updatedAt)})`);
  for (const campo of campos) {
    if (stable(draftData[campo]) === stable(liveData[campo])) continue;
    console.log(`    ${campo}`);
    console.log(`      borrador : ${resumen(draftData[campo])}`);
    console.log(`      publicado: ${resumen(liveData[campo])}`);
  }
}

if (obsoletos.length === 0) {
  console.log("\nNo hay borradores obsoletos. Nada que hacer.");
  process.exit(0);
}

console.log(`\n${obsoletos.length} borrador(es) obsoleto(s): ${obsoletos.map((o) => o.id).join(", ")}`);
console.log("Los documentos PUBLICADOS no se tocan: esto solo borra el borrador del panel.");

if (!erase) {
  console.log("\nMODO DE PRUEBA: no se borró nada. Para borrar de verdad: node limpiar-borradores.mjs --borrar");
  process.exit(0);
}

const rl = createInterface({ input: process.stdin, output: process.stdout });
const answer = (await rl.question(`¿Borrar ${obsoletos.length} borrador(es) obsoleto(s)? (s/N) `)).trim().toLowerCase();
rl.close();
if (!["s", "si", "sí"].includes(answer)) {
  console.log("Cancelado: no se borró nada.");
  process.exit(0);
}

const batch = db.batch();
for (const { id } of obsoletos) batch.delete(db.collection("drafts").doc(id));
await batch.commit();
console.log(`Borrados: ${obsoletos.map((o) => o.id).join(", ")}. El panel partirá de lo publicado.`);
