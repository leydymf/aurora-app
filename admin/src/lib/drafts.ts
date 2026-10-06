// Flujo borrador → publicado (base de RF-44) SIN tocar el esquema que la app
// consume. La app lee las colecciones vivas COMPLETAS y sin filtros
// (FirestoreContentRemoteSource / FirestoreQuestionnaireRemoteSource), así que
// los borradores no pueden convivir con lo publicado: viven en la colección
// `drafts`, que la app jamás consulta y que firestore.rules restringe al
// claim admin. Publicar copia `data` LITERAL al documento vivo — el estado y
// las marcas de tiempo quedan SOLO en el borrador y las colecciones vivas
// conservan el esquema actual byte a byte (ningún campo nuevo).
import {
    collection,
    doc,
    getDocs,
    query,
    serverTimestamp,
    setDoc,
    Timestamp,
    where,
    writeBatch,
    type DocumentReference,
} from 'firebase/firestore'
import { db } from '../firebase/config'
import { asRecord } from './parse'

/** Colecciones vivas que la app Android lee (nombres neutros, RNF-05). */
export type ResourceCollection = 'topics' | 'routes' | 'agencies' | 'lines' | 'forms'

export type DraftStatus = 'borrador' | 'publicado'

/**
 * Vista combinada de un recurso: borrador (si existe) + documento vivo.
 * `data` es la versión que se edita y publica: el borrador cuando lo hay;
 * si no, lo vivo (documentos sembrados antes de que existiera el panel).
 *
 * Excepción importante (2026-10-06): un borrador marcado como «publicado» que
 * NO coincide con el documento vivo está obsoleto — lo vivo se publicó por otra
 * vía (la siembra o `publicar-tipos`) y el borrador se quedó congelado en lo que
 * se editó la última vez en el panel. Editarlo y publicarlo sobrescribiría
 * contenido validado con texto viejo, así que en ese caso `data` es lo VIVO.
 */
export interface ResourceEntry<T> {
    readonly docId: string
    readonly status: DraftStatus
    readonly data: T
    readonly liveData: T | null
    readonly hasDraft: boolean
    readonly updatedAt: Date | null
    readonly publishedAt: Date | null
    /** El borrador estaba obsoleto y se descartó en favor de lo publicado. */
    readonly staleDraftDiscarded: boolean
    /** Hay un borrador en edición que difiere de lo publicado (aviso antes de publicar). */
    readonly differsFromLive: boolean
}

const DRAFTS_COLLECTION = 'drafts'

function liveRef(resource: ResourceCollection, docId: string): DocumentReference {
    return doc(db, resource, docId)
}

function draftRef(resource: ResourceCollection, docId: string): DocumentReference {
    // Id compuesto: los ids opacos no llevan `_`, así que `__` no colisiona.
    return doc(db, DRAFTS_COLLECTION, `${resource}__${docId}`)
}

function toDate(value: unknown): Date | null {
    return value instanceof Timestamp ? value.toDate() : null
}

/**
 * Decide qué versión edita y publica el panel para UN documento. Es pura a
 * propósito: es la regla que impide que un borrador viejo sobrescriba contenido
 * validado, y así puede probarse sin Firestore.
 *
 * - Sin documento vivo (recurso nuevo): manda el borrador.
 * - Borrador marcado «publicado» que NO coincide con lo vivo: está obsoleto —
 *   alguien publicó por otra vía (la siembra, `publicar-tipos`) y el borrador
 *   se quedó atrás. Se edita lo VIVO y se avisa.
 * - Borrador «en edición»: manda el borrador, que es trabajo real sin publicar.
 *
 * `differsFromLive` dice si lo que se publicaría cambia lo que hoy ve la app,
 * para que el panel lo advierta antes de tocar «Publicar».
 */
export function combine<T>(
    docId: string,
    status: DraftStatus,
    draftData: T,
    liveData: T | null,
    updatedAt: Date | null,
    publishedAt: Date | null,
): ResourceEntry<T> {
    // `liveData` se comprueba aquí una sola vez: si es nulo no hay con qué
    // comparar y el borrador manda (recurso nuevo, aún sin publicar).
    const stale = status === 'publicado' && liveData !== null && !sameContent(draftData, liveData)
    const data = stale ? (liveData as T) : draftData
    return {
        docId,
        status,
        data,
        liveData,
        hasDraft: true,
        updatedAt,
        publishedAt,
        staleDraftDiscarded: stale,
        // Lo que se publicaría NO es lo que hoy ve la app: el panel lo advierte.
        differsFromLive: liveData !== null && !sameContent(data, liveData),
    }
}

/** Comparación estructural estable: el orden de las claves no debe importar. */
function sameContent(a: unknown, b: unknown): boolean {
    return stableJson(a) === stableJson(b)
}

function stableJson(value: unknown): string {
    return JSON.stringify(value, (_key, raw: unknown) => {
        if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) return raw
        const record = raw as Record<string, unknown>
        return Object.keys(record)
            .sort()
            .reduce<Record<string, unknown>>((acc, key) => {
                acc[key] = record[key]
                return acc
            }, {})
    })
}

/** Carga vivo + borradores de un recurso y los combina por id de documento. */
export async function loadEntries<T>(
    resource: ResourceCollection,
    fromRaw: (raw: Record<string, unknown>) => T,
): Promise<ResourceEntry<T>[]> {
    const [liveSnapshot, draftSnapshot] = await Promise.all([
        getDocs(collection(db, resource)),
        getDocs(query(collection(db, DRAFTS_COLLECTION), where('collection', '==', resource))),
    ])

    const liveById = new Map<string, T>()
    for (const document of liveSnapshot.docs) {
        liveById.set(document.id, fromRaw(asRecord(document.data())))
    }

    const entries = new Map<string, ResourceEntry<T>>()
    for (const document of draftSnapshot.docs) {
        const raw = asRecord(document.data())
        const docId = typeof raw.docId === 'string' ? raw.docId : ''
        if (isBlankId(docId)) continue
        const status: DraftStatus = raw.status === 'publicado' ? 'publicado' : 'borrador'
        const draftData = fromRaw(asRecord(raw.data))
        const liveData = liveById.get(docId) ?? null
        entries.set(
            docId,
            combine(
                docId,
                status,
                draftData,
                liveData,
                toDate(raw.updatedAt),
                toDate(raw.publishedAt),
            ),
        )
    }
    for (const [docId, live] of liveById) {
        if (entries.has(docId)) continue
        // Documento sembrado antes del panel: ya está en la app, sin borrador.
        entries.set(docId, {
            docId,
            status: 'publicado',
            data: live,
            liveData: live,
            hasDraft: false,
            updatedAt: null,
            publishedAt: null,
            staleDraftDiscarded: false,
            differsFromLive: false,
        })
    }
    return [...entries.values()]
}

function isBlankId(docId: string): boolean {
    return docId.trim().length === 0
}

/**
 * Índice orden→nombre para validar unicidad de `order` al publicar.
 * Considera el borrador Y la versión publicada de cada documento: publicar un
 * orden que ya está vivo en otro documento dejaría dos publicados iguales.
 */
export function buildOrderIndex<T extends { order: number }>(
    entries: readonly ResourceEntry<T>[] | null,
    excludeId: string | null,
    labelOf: (data: T) => string,
): Map<number, string> {
    const orders = new Map<number, string>()
    for (const entry of entries ?? []) {
        if (entry.docId === excludeId) continue
        const label = labelOf(entry.data) || entry.docId
        orders.set(entry.data.order, label)
        if (entry.liveData !== null && entry.liveData.order !== entry.data.order) {
            orders.set(entry.liveData.order, label)
        }
    }
    return orders
}

/** Guarda (o crea) el borrador; lo vivo no se toca hasta publicar. */
export async function saveDraft(
    resource: ResourceCollection,
    docId: string,
    data: Record<string, unknown>,
    publishedAt: Date | null,
): Promise<void> {
    await setDoc(draftRef(resource, docId), {
        collection: resource,
        docId,
        status: 'borrador',
        data,
        updatedAt: serverTimestamp(),
        publishedAt: publishedAt ? Timestamp.fromDate(publishedAt) : null,
    })
}

/**
 * Publica: copia `data` tal cual al documento vivo (lo único que la app ve)
 * y marca el borrador como publicado, en un lote atómico.
 */
export async function publishEntry(
    resource: ResourceCollection,
    docId: string,
    data: Record<string, unknown>,
): Promise<void> {
    const batch = writeBatch(db)
    batch.set(liveRef(resource, docId), data)
    batch.set(draftRef(resource, docId), {
        collection: resource,
        docId,
        status: 'publicado',
        data,
        updatedAt: serverTimestamp(),
        publishedAt: serverTimestamp(),
    })
    await batch.commit()
}

/** Elimina el recurso completo: documento vivo y borrador, en un lote. */
export async function deleteEntry(resource: ResourceCollection, docId: string): Promise<void> {
    const batch = writeBatch(db)
    batch.delete(liveRef(resource, docId))
    batch.delete(draftRef(resource, docId))
    await batch.commit()
}
