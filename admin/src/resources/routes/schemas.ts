// Esquemas del recurso «Rutas y líneas» (RF-42), espejo EXACTO de lo que la
// app Android lee: RouteDto/AgencyDto/LineDto (content/data/remote/
// ContentDtos.kt) y sus campos obligatorios en ContentMappers.kt. Agregar o
// renombrar un campo es un cambio de esquema y requiere decisión explícita.
import { num, str, strList, strOrNull } from '../../lib/parse'
import { phoneIssueMessage } from '../../lib/phone'
import { containsDraftMarker, isBlank, type ValidationIssue } from '../../lib/validation'

/** Espejo de SUPPORTED_SCHEMA_VERSION de la app (ContentMappers.kt). */
export const SCHEMA_VERSION = 1

export type RouteDoc = {
    schemaVersion: number
    order: number
    title: string
    summary: string
    agencyIds: string[]
}

export type AgencyDoc = {
    schemaVersion: number
    name: string
    role: string
    whenToUse: string
    phones: string[]
    address: string | null
    hours: string | null
}

export type LineDoc = {
    schemaVersion: number
    order: number
    name: string
    number: string
    description: string
    hours: string | null
}

// ---- Lectura tolerante (espejo de los DTOs de la app) ----

export function routeFromRaw(raw: Record<string, unknown>): RouteDoc {
    return {
        schemaVersion: num(raw, 'schemaVersion', SCHEMA_VERSION),
        order: num(raw, 'order'),
        title: str(raw, 'title'),
        summary: str(raw, 'summary'),
        agencyIds: strList(raw, 'agencyIds'),
    }
}

export function agencyFromRaw(raw: Record<string, unknown>): AgencyDoc {
    return {
        schemaVersion: num(raw, 'schemaVersion', SCHEMA_VERSION),
        name: str(raw, 'name'),
        role: str(raw, 'role'),
        whenToUse: str(raw, 'whenToUse'),
        phones: strList(raw, 'phones'),
        address: strOrNull(raw, 'address'),
        hours: strOrNull(raw, 'hours'),
    }
}

export function lineFromRaw(raw: Record<string, unknown>): LineDoc {
    return {
        schemaVersion: num(raw, 'schemaVersion', SCHEMA_VERSION),
        order: num(raw, 'order'),
        name: str(raw, 'name'),
        number: str(raw, 'number'),
        description: str(raw, 'description'),
        hours: strOrNull(raw, 'hours'),
    }
}

// ---- Plantillas para crear ----

export function emptyRoute(order: number): RouteDoc {
    return { schemaVersion: SCHEMA_VERSION, order, title: '', summary: '', agencyIds: [] }
}

export function emptyAgency(): AgencyDoc {
    return {
        schemaVersion: SCHEMA_VERSION,
        name: '',
        role: '',
        whenToUse: '',
        phones: [],
        address: null,
        hours: null,
    }
}

export function emptyLine(order: number): LineDoc {
    return {
        schemaVersion: SCHEMA_VERSION,
        order,
        name: '',
        number: '',
        description: '',
        hours: null,
    }
}

// ---- Limpieza previa a guardar ----

function trimmedOrNull(value: string | null): string | null {
    if (value === null) return null
    const trimmed = value.trim()
    return trimmed.length === 0 ? null : trimmed
}

export function normalizeRoute(route: RouteDoc): RouteDoc {
    return {
        ...route,
        title: route.title.trim(),
        summary: route.summary.trim(),
        agencyIds: [...route.agencyIds],
    }
}

export function normalizeAgency(agency: AgencyDoc): AgencyDoc {
    return {
        ...agency,
        name: agency.name.trim(),
        role: agency.role.trim(),
        whenToUse: agency.whenToUse.trim(),
        // Un renglón de teléfono agregado y dejado vacío es ruido, no dato.
        phones: agency.phones.map((phone) => phone.trim()).filter((phone) => phone.length > 0),
        address: trimmedOrNull(agency.address),
        hours: trimmedOrNull(agency.hours),
    }
}

export function normalizeLine(line: LineDoc): LineDoc {
    return {
        ...line,
        name: line.name.trim(),
        number: line.number.trim(),
        description: line.description.trim(),
        hours: trimmedOrNull(line.hours),
    }
}

// ---- Validación dura para PUBLICAR ----

function checkOrder(order: number, takenBy: ReadonlyMap<number, string>): ValidationIssue[] {
    const issues: ValidationIssue[] = []
    if (!Number.isInteger(order) || order < 1) {
        issues.push({ field: 'order', message: 'El orden debe ser un entero mayor o igual a 1.' })
    }
    const conflict = takenBy.get(order)
    if (conflict !== undefined) {
        issues.push({
            field: 'order',
            message: `El orden ${String(order)} ya lo usa ${conflict}.`,
        })
    }
    return issues
}

// Primera compuerta de la app (isSupportedSchema, ContentMappers.kt): una
// versión extraña haría que la app ignore el documento en silencio.
function checkSchemaVersion(schemaVersion: number): ValidationIssue[] {
    if (schemaVersion === SCHEMA_VERSION) return []
    return [
        {
            field: 'general',
            message: `schemaVersion ${String(schemaVersion)} no es la soportada (${String(SCHEMA_VERSION)}).`,
        },
    ]
}

function checkDraftMarker(data: unknown): ValidationIssue[] {
    if (!containsDraftMarker(data)) return []
    return [
        {
            field: 'general',
            message:
                'Contiene el marcador [POR CONFIRMAR]: verifica el dato con la fuente oficial antes de publicar.',
        },
    ]
}

/**
 * Una ruta solo se publica si TODAS sus entidades están publicadas: la app
 * resuelve `agencyIds` contra la colección viva `agencies` y omite en
 * silencio las referencias sin resolver (ContentMappers.kt, RF-42).
 */
export function validateRoute(
    route: RouteDoc,
    orderTakenBy: ReadonlyMap<number, string>,
    publishedAgencyIds: ReadonlySet<string>,
    agencyNames: ReadonlyMap<string, string>,
): ValidationIssue[] {
    const issues = [
        ...checkSchemaVersion(route.schemaVersion),
        ...checkOrder(route.order, orderTakenBy),
    ]
    if (isBlank(route.title)) {
        issues.push({ field: 'title', message: 'El título es obligatorio.' })
    }
    if (isBlank(route.summary)) {
        issues.push({ field: 'summary', message: 'El resumen es obligatorio.' })
    }
    for (const agencyId of route.agencyIds) {
        if (!publishedAgencyIds.has(agencyId)) {
            const name = agencyNames.get(agencyId) ?? agencyId
            issues.push({
                field: 'agencyIds',
                message: `La entidad «${name}» no está publicada: publícala primero (o quítala de la ruta).`,
            })
        }
    }
    issues.push(...checkDraftMarker(route))
    return issues
}

export function validateAgency(agency: AgencyDoc): ValidationIssue[] {
    const issues = checkSchemaVersion(agency.schemaVersion)
    if (isBlank(agency.name)) {
        issues.push({ field: 'name', message: 'El nombre es obligatorio.' })
    }
    if (isBlank(agency.role)) {
        issues.push({ field: 'role', message: 'El campo «Qué hace» es obligatorio.' })
    }
    if (isBlank(agency.whenToUse)) {
        issues.push({ field: 'whenToUse', message: 'El campo «Cuándo acudir» es obligatorio.' })
    }
    for (const phone of agency.phones) {
        const problem = phoneIssueMessage(phone)
        if (problem !== null) {
            issues.push({ field: 'phones', message: problem })
        }
    }
    issues.push(...checkDraftMarker(agency))
    return issues
}

export function validateLine(
    line: LineDoc,
    orderTakenBy: ReadonlyMap<number, string>,
): ValidationIssue[] {
    const issues = [
        ...checkSchemaVersion(line.schemaVersion),
        ...checkOrder(line.order, orderTakenBy),
    ]
    if (isBlank(line.name)) {
        issues.push({ field: 'name', message: 'El nombre es obligatorio.' })
    }
    if (isBlank(line.description)) {
        issues.push({ field: 'description', message: 'La descripción es obligatoria.' })
    }
    if (isBlank(line.number)) {
        issues.push({ field: 'number', message: 'El número es obligatorio.' })
    } else {
        const problem = phoneIssueMessage(line.number)
        if (problem !== null) {
            issues.push({ field: 'number', message: problem })
        }
    }
    issues.push(...checkDraftMarker(line))
    return issues
}
