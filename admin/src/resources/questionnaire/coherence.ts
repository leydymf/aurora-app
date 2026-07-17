// Validaciones de coherencia del cuestionario (RF-43). Son el espejo de lo
// que el motor de la app exige y asume — fuentes:
//  - QuestionnaireMappers.kt: la app descarta el documento COMPLETO si
//    cualquier pieza es inválida (criterio 2 de RF-43): publicar algo
//    incompleto dejaría a las usuarias sin cuestionario.
//  - CalculateGuidanceLevelUseCase.kt: puntaje = Σ (peso × valor de la opción
//    elegida); nivel base = el de mayor minScore <= puntaje (si ninguno
//    alcanza, cae a un fallback — configuración ambigua que aquí se impide);
//    el piso de una pregunta crítica (alertLevelId) se dispara al elegir la
//    opción de MAYOR valor de la escala, por eso el máximo no puede repetirse.
// Funciones puras: son las validaciones críticas y llevan test (coherence.test.ts).
import { containsDraftMarker, isBlank, type ValidationIssue } from '../../lib/validation'
import { SCHEMA_VERSION, type FormDoc } from './formSchema'

/** Puntaje máximo alcanzable: Σ(pesos) × valor máximo de la escala. */
export function maxAchievableScore(form: FormDoc): number {
    if (form.scale.length === 0 || form.questions.length === 0) return 0
    const maxValue = Math.max(...form.scale.map((option) => option.value))
    const totalWeight = form.questions.reduce((sum, question) => sum + question.weight, 0)
    return maxValue * totalWeight
}

/**
 * Validación dura para PUBLICAR el cuestionario. `publishedRouteIds` son las
 * rutas ya publicadas (lo único que la app puede resolver); `routeNames`
 * sirve para los mensajes.
 */
export function validateQuestionnaire(
    form: FormDoc,
    publishedRouteIds: ReadonlySet<string>,
    routeNames: ReadonlyMap<string, string>,
): ValidationIssue[] {
    const issues: ValidationIssue[] = []

    if (form.schemaVersion !== SCHEMA_VERSION) {
        // Primera compuerta de la app (isSupportedSchema): con una versión
        // extraña, la app ignoraría el documento en silencio.
        issues.push({
            field: 'general',
            message: `schemaVersion ${String(form.schemaVersion)} no es la soportada (${String(SCHEMA_VERSION)}).`,
        })
    }
    if (isBlank(form.title)) {
        issues.push({ field: 'title', message: 'El título es obligatorio.' })
    }
    if (isBlank(form.intro)) {
        issues.push({ field: 'intro', message: 'La introducción es obligatoria.' })
    }
    if (isBlank(form.notice)) {
        issues.push({
            field: 'notice',
            message: 'El aviso («es una orientación, no un diagnóstico») es obligatorio.',
        })
    }

    validateScale(form, issues)
    validateQuestions(form, issues)
    validateLevels(form, issues)
    validateRouteReferences(form, publishedRouteIds, routeNames, issues)

    if (containsDraftMarker(form)) {
        issues.push({
            field: 'general',
            message:
                'Contiene el marcador [POR CONFIRMAR]: retíralo tras la validación de la directora ' +
                '(si está en la nota interna, vacíala).',
        })
    }
    return issues
}

function validateScale(form: FormDoc, issues: ValidationIssue[]): void {
    if (form.scale.length < 2) {
        issues.push({
            field: 'scale',
            message: 'La escala necesita al menos 2 opciones para discriminar respuestas.',
        })
    }
    const values = new Set<number>()
    let previousValue: number | null = null
    form.scale.forEach((option, index) => {
        const position = String(index + 1)
        if (isBlank(option.label)) {
            issues.push({ field: 'scale', message: `La opción ${position} no tiene etiqueta.` })
        }
        if (!Number.isInteger(option.value) || option.value < 0) {
            issues.push({
                field: 'scale',
                message: `El valor de la opción ${position} debe ser un entero ≥ 0.`,
            })
        }
        if (values.has(option.value)) {
            issues.push({
                field: 'scale',
                message:
                    `El valor ${String(option.value)} está repetido: el piso crítico se ancla en la ` +
                    'opción de MAYOR valor (motor de la app) y un máximo repetido lo vuelve ambiguo.',
            })
        }
        values.add(option.value)
        if (previousValue !== null && option.value <= previousValue) {
            issues.push({
                field: 'scale',
                message:
                    `Los valores deben crecer con el orden de las opciones (la opción ${position} ` +
                    'no puede valer menos que la anterior): la última opción es la más severa.',
            })
        }
        previousValue = option.value
    })
    checkUniqueIds(
        form.scale.map((option) => option.id),
        'scale',
        'opciones de la escala',
        issues,
    )
}

function validateQuestions(form: FormDoc, issues: ValidationIssue[]): void {
    if (form.questions.length === 0) {
        issues.push({
            field: 'questions',
            message: 'Sin preguntas no hay cuestionario que publicar (criterio 2 de RF-43).',
        })
    }
    const levelIds = new Set(form.levels.map((level) => level.id))
    form.questions.forEach((question, index) => {
        const position = String(index + 1)
        if (isBlank(question.text)) {
            issues.push({
                field: 'questions',
                message: `La pregunta ${position} no tiene texto.`,
            })
        }
        if (!Number.isInteger(question.weight) || question.weight < 1) {
            issues.push({
                field: 'questions',
                message:
                    `El peso de la pregunta ${position} debe ser un entero ≥ 1: el motor suma ` +
                    'peso × valor y un peso 0 anula la pregunta (uno negativo rompe los umbrales).',
            })
        }
        if (question.alertLevelId !== undefined && !levelIds.has(question.alertLevelId)) {
            issues.push({
                field: 'questions',
                message: `El nivel crítico de la pregunta ${position} («${question.alertLevelId}») no existe.`,
            })
        }
    })
    checkUniqueIds(
        form.questions.map((question) => question.id),
        'questions',
        'preguntas',
        issues,
    )
}

function validateLevels(form: FormDoc, issues: ValidationIssue[]): void {
    if (form.levels.length === 0) {
        issues.push({
            field: 'levels',
            message: 'Sin niveles no hay resultado que mostrar (criterio 2 de RF-43).',
        })
        return
    }
    form.levels.forEach((level, index) => {
        const position = String(index + 1)
        if (isBlank(level.title)) {
            issues.push({ field: 'levels', message: `El nivel ${position} no tiene nombre.` })
        }
        if (isBlank(level.summary)) {
            issues.push({ field: 'levels', message: `El nivel ${position} no tiene descripción.` })
        }
        level.advice.forEach((item, adviceIndex) => {
            if (isBlank(item)) {
                issues.push({
                    field: 'levels',
                    message: `El consejo ${String(adviceIndex + 1)} del nivel ${position} está vacío.`,
                })
            }
        })
        if (!Number.isInteger(level.minScore) || level.minScore < 0) {
            issues.push({
                field: 'levels',
                message: `El umbral del nivel ${position} debe ser un entero ≥ 0.`,
            })
        }
    })

    // Nivel de arranque: sin un umbral 0, el motor cae a un fallback ambiguo
    // (CalculateGuidanceLevelUseCase: «si ningún umbral alcanza, cae al nivel
    // de menor severidad») — el panel lo impide.
    const starters = form.levels.filter((level) => level.minScore === 0)
    if (starters.length === 0) {
        issues.push({
            field: 'levels',
            message:
                'Debe existir un nivel de arranque con umbral 0 (todo puntaje cae en algún nivel).',
        })
    }

    // Umbrales estrictamente crecientes con la severidad (el orden del arreglo):
    // dos niveles con el mismo umbral son una configuración ambigua.
    for (let index = 1; index < form.levels.length; index++) {
        const previous = form.levels[index - 1]
        const current = form.levels[index]
        if (previous === undefined || current === undefined) continue
        if (current.minScore <= previous.minScore) {
            issues.push({
                field: 'levels',
                message:
                    `Los umbrales deben crecer estrictamente con la severidad: el nivel ${String(index + 1)} ` +
                    `(umbral ${String(current.minScore)}) no supera al anterior (${String(previous.minScore)}).`,
            })
        }
    }

    // Niveles inalcanzables: un umbral por encima del puntaje máximo posible
    // jamás se mostraría a ninguna usuaria.
    const maxScore = maxAchievableScore(form)
    if (form.scale.length > 0 && form.questions.length > 0) {
        for (const level of form.levels) {
            if (level.minScore > maxScore) {
                issues.push({
                    field: 'levels',
                    message:
                        `El nivel «${level.title || level.id}» es inalcanzable: su umbral ` +
                        `(${String(level.minScore)}) supera el puntaje máximo posible (${String(maxScore)}).`,
                })
            }
        }
    }

    checkUniqueIds(
        form.levels.map((level) => level.id),
        'levels',
        'niveles',
        issues,
    )
}

function validateRouteReferences(
    form: FormDoc,
    publishedRouteIds: ReadonlySet<string>,
    routeNames: ReadonlyMap<string, string>,
    issues: ValidationIssue[],
): void {
    form.levels.forEach((level, index) => {
        for (const routeId of level.routeIds) {
            if (!publishedRouteIds.has(routeId)) {
                const name = routeNames.get(routeId) ?? routeId
                issues.push({
                    field: 'levels',
                    message:
                        `El nivel ${String(index + 1)} recomienda la ruta «${name}», que no está ` +
                        'publicada: publícala primero (o quítala del nivel).',
                })
            }
        }
    })
}

/**
 * La app elige el formulario VIVO de mayor `version`
 * (FirestoreQuestionnaireRepository.kt): un borrador cuya versión no supere
 * la publicada más alta no regiría al publicarse — o empataría, que es
 * ambiguo. La versión se re-estampa en cada guardado; este chequeo cierra la
 * ventana entre guardar y publicar cuando OTRO cuestionario se publicó en
 * medio (posible desde que existen varios).
 */
export function validatePublishVersion(form: FormDoc, maxLiveVersion: number): ValidationIssue[] {
    if (form.version > maxLiveVersion) return []
    return [
        {
            field: 'version',
            message:
                `La versión del borrador (${String(form.version)}) no supera la publicada más ` +
                `alta (${String(maxLiveVersion)}): guarda el borrador de nuevo para actualizarla.`,
        },
    ]
}

function checkUniqueIds(
    ids: readonly string[],
    field: string,
    label: string,
    issues: ValidationIssue[],
): void {
    const seen = new Set<string>()
    for (const id of ids) {
        if (id === '' || seen.has(id)) {
            issues.push({
                field,
                message: `Hay ${label} con id vacío o repetido («${id}»): edítalas desde el panel.`,
            })
            return
        }
        seen.add(id)
    }
}
