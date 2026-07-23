// Validaciones de coherencia del cuestionario validado v2 (RF-43). Son el
// espejo de lo que el motor de la app exige y asume — fuentes:
//  - QuestionnaireMappers.kt: la app descarta el documento COMPLETO si cualquier
//    pieza estructural es inválida (criterio 2 de RF-43). Tolera referencias
//    huérfanas de dimensiones y banderas, pero el panel las impide al publicar.
//  - CalculateGuidanceLevelUseCase.kt: Track 1 (dimensiones ≥ minValue) y Track 2
//    (puntaje = Σ peso×valor; nivel base por umbral; banderas de umbral y de
//    conteo que solo elevan). Documento validado §5.
// Funciones puras: son las validaciones críticas y llevan test (coherence.test.ts).
import { containsDraftMarker, isBlank, type ValidationIssue } from '../../lib/validation'
import { SCHEMA_VERSION, type FormDoc } from './formSchema'

/** Conjuntos y nombres de líneas y entidades publicadas, para validar los accesos. */
export interface PublishedDirectory {
    readonly lineIds: ReadonlySet<string>
    readonly agencyIds: ReadonlySet<string>
    readonly lineNames: ReadonlyMap<string, string>
    readonly agencyNames: ReadonlyMap<string, string>
}

/** Valor máximo de cada escala por id (para el puntaje y los umbrales de bandera). */
function scaleMaxValues(form: FormDoc): Map<string, number> {
    const maxByScale = new Map<string, number>()
    for (const scale of form.scales) {
        maxByScale.set(
            scale.id,
            scale.options.reduce((max, option) => Math.max(max, option.value), 0),
        )
    }
    return maxByScale
}

/** Puntaje máximo alcanzable: Σ (peso × valor máximo de la escala de cada pregunta). */
export function maxAchievableScore(form: FormDoc): number {
    const maxByScale = scaleMaxValues(form)
    return form.questions.reduce(
        (sum, question) => sum + question.weight * (maxByScale.get(question.scaleId) ?? 0),
        0,
    )
}

/**
 * Validación dura para PUBLICAR el cuestionario. [published] son las líneas y
 * entidades ya publicadas (lo único que la app puede resolver en los accesos).
 */
export function validateQuestionnaire(
    form: FormDoc,
    published: PublishedDirectory,
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
            message: 'El aviso permanente de alcance orientador (RF-30) es obligatorio.',
        })
    }
    if (isBlank(form.recognition.prefix) || isBlank(form.recognition.empty)) {
        issues.push({
            field: 'recognition',
            message:
                'La línea de reconocimiento necesita su texto de encabezado y su texto de ausencia.',
        })
    }

    validateScales(form, issues)
    validateBlocks(form, issues)
    validateQuestions(form, issues)
    validateDimensions(form, issues)
    validateMunicipalities(form, issues)
    validateLevels(form, issues)
    validateFlags(form, issues)
    validateAccessReferences(form, published, issues)

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

function validateScales(form: FormDoc, issues: ValidationIssue[]): void {
    if (form.scales.length === 0) {
        issues.push({ field: 'scales', message: 'Debe existir al menos una escala de respuesta.' })
    }
    // Solo las escalas de preguntas PUNTUABLES aportan al puntaje: en ellas los
    // valores deben crecer y no repetirse. La escala de una pregunta de contexto
    // (vínculo) es categórica —todas sus opciones valen 0— y esas reglas no aplican.
    const scoringScaleIds = new Set(
        form.questions.filter((question) => !question.context).map((question) => question.scaleId),
    )
    form.scales.forEach((scale, scaleIndex) => {
        const label = scale.id || `#${String(scaleIndex + 1)}`
        const scoring = scoringScaleIds.has(scale.id)
        if (scoring && scale.options.length < 2) {
            issues.push({
                field: 'scales',
                message: `La escala «${label}» necesita al menos 2 opciones para discriminar respuestas.`,
            })
        }
        const values = new Set<number>()
        let previousValue: number | null = null
        scale.options.forEach((option, index) => {
            const position = `${label} · opción ${String(index + 1)}`
            if (isBlank(option.label)) {
                issues.push({
                    field: 'scales',
                    message: `La opción ${position} no tiene etiqueta.`,
                })
            }
            if (!Number.isInteger(option.value) || option.value < 0) {
                issues.push({
                    field: 'scales',
                    message: `El valor de ${position} debe ser un entero ≥ 0.`,
                })
            }
            if (scoring && values.has(option.value)) {
                issues.push({
                    field: 'scales',
                    message: `El valor ${String(option.value)} está repetido en la escala «${label}».`,
                })
            }
            values.add(option.value)
            if (scoring && previousValue !== null && option.value <= previousValue) {
                issues.push({
                    field: 'scales',
                    message: `Los valores de la escala «${label}» deben crecer con el orden de las opciones.`,
                })
            }
            previousValue = option.value
        })
        checkUniqueIds(
            scale.options.map((option) => option.id),
            'scales',
            `opciones de la escala «${label}»`,
            issues,
        )
    })
    checkUniqueIds(
        form.scales.map((scale) => scale.id),
        'scales',
        'escalas',
        issues,
    )
}

function validateBlocks(form: FormDoc, issues: ValidationIssue[]): void {
    if (form.blocks.length === 0) {
        issues.push({ field: 'blocks', message: 'Debe existir al menos un bloque.' })
    }
    form.blocks.forEach((block, index) => {
        if (isBlank(block.title)) {
            issues.push({
                field: 'blocks',
                message: `El bloque ${String(index + 1)} no tiene título.`,
            })
        }
    })
    checkUniqueIds(
        form.blocks.map((block) => block.id),
        'blocks',
        'bloques',
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
    const blockIds = new Set(form.blocks.map((block) => block.id))
    const scaleIds = new Set(form.scales.map((scale) => scale.id))
    form.questions.forEach((question, index) => {
        const position = String(index + 1)
        if (isBlank(question.text)) {
            issues.push({ field: 'questions', message: `La pregunta ${position} no tiene texto.` })
        }
        if (!blockIds.has(question.blockId)) {
            issues.push({
                field: 'questions',
                message: `La pregunta ${position} referencia un bloque inexistente («${question.blockId}»).`,
            })
        }
        if (!scaleIds.has(question.scaleId)) {
            issues.push({
                field: 'questions',
                message: `La pregunta ${position} referencia una escala inexistente («${question.scaleId}»).`,
            })
        }
        if (!Number.isInteger(question.weight) || question.weight < 0) {
            issues.push({
                field: 'questions',
                message: `El peso de la pregunta ${position} debe ser un entero ≥ 0.`,
            })
        }
        if (question.context && question.weight !== 0) {
            issues.push({
                field: 'questions',
                message: `La pregunta de contexto ${position} no puntúa: su peso debe ser 0.`,
            })
        }
        if (!question.context && question.weight < 1) {
            issues.push({
                field: 'questions',
                message:
                    `El peso de la pregunta puntuable ${position} debe ser ≥ 1: ` +
                    'un peso 0 la anula (marca «de contexto» si no debe puntuar).',
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

function validateDimensions(form: FormDoc, issues: ValidationIssue[]): void {
    if (form.dimensions.length === 0) {
        issues.push({
            field: 'dimensions',
            message: 'Debe existir al menos una dimensión de reconocimiento.',
        })
    }
    const questionIds = new Set(form.questions.map((question) => question.id))
    form.dimensions.forEach((dimension, index) => {
        const position = String(index + 1)
        if (isBlank(dimension.label)) {
            issues.push({
                field: 'dimensions',
                message: `La dimensión ${position} no tiene etiqueta.`,
            })
        }
        if (!Number.isInteger(dimension.minValue) || dimension.minValue < 1) {
            issues.push({
                field: 'dimensions',
                message: `El umbral de encendido de la dimensión ${position} debe ser un entero ≥ 1.`,
            })
        }
        if (dimension.questionIds.length === 0) {
            issues.push({
                field: 'dimensions',
                message: `La dimensión ${position} no referencia ninguna pregunta.`,
            })
        }
        for (const questionId of dimension.questionIds) {
            if (!questionIds.has(questionId)) {
                issues.push({
                    field: 'dimensions',
                    message: `La dimensión ${position} referencia una pregunta inexistente («${questionId}»).`,
                })
            }
        }
    })
    checkUniqueIds(
        form.dimensions.map((dimension) => dimension.id),
        'dimensions',
        'dimensiones',
        issues,
    )
}

function validateMunicipalities(form: FormDoc, issues: ValidationIssue[]): void {
    if (form.municipalities.length === 0) {
        issues.push({
            field: 'municipalities',
            message: 'Debe existir al menos un municipio para los accesos.',
        })
    }
    form.municipalities.forEach((municipality, index) => {
        if (isBlank(municipality.label)) {
            issues.push({
                field: 'municipalities',
                message: `El municipio ${String(index + 1)} no tiene nombre.`,
            })
        }
    })
    checkUniqueIds(
        form.municipalities.map((municipality) => municipality.id),
        'municipalities',
        'municipios',
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
    // (CalculateGuidanceLevelUseCase) — el panel lo impide.
    const starters = form.levels.filter((level) => level.minScore === 0)
    if (starters.length === 0) {
        issues.push({
            field: 'levels',
            message:
                'Debe existir un nivel de arranque con umbral 0 (todo puntaje cae en algún nivel).',
        })
    }

    // Umbrales estrictamente crecientes con la severidad (el orden del arreglo):
    // parten el rango 0–máximo en intervalos contiguos SIN huecos ni solapes.
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
    if (form.scales.length > 0 && form.questions.length > 0) {
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

function validateFlags(form: FormDoc, issues: ValidationIssue[]): void {
    const questionIds = new Set(form.questions.map((question) => question.id))
    const levelIds = new Set(form.levels.map((level) => level.id))
    const maxByScale = scaleMaxValues(form)
    const questionsById = new Map(form.questions.map((question) => [question.id, question]))

    form.thresholdFlags.forEach((flag, index) => {
        const position = String(index + 1)
        if (!questionIds.has(flag.questionId)) {
            issues.push({
                field: 'flags',
                message: `La bandera de umbral ${position} referencia una pregunta inexistente («${flag.questionId}»).`,
            })
        }
        if (!levelIds.has(flag.levelId)) {
            issues.push({
                field: 'flags',
                message: `La bandera de umbral ${position} referencia un nivel inexistente («${flag.levelId}»).`,
            })
        }
        const question = questionsById.get(flag.questionId)
        const scaleMax = question ? (maxByScale.get(question.scaleId) ?? 0) : 0
        if (
            !Number.isInteger(flag.minValue) ||
            flag.minValue < 1 ||
            (question !== undefined && flag.minValue > scaleMax)
        ) {
            issues.push({
                field: 'flags',
                message:
                    `El umbral de la bandera ${position} debe ser un entero entre 1 y el valor máximo ` +
                    `de la escala de la pregunta (${String(scaleMax)}).`,
            })
        }
    })

    form.countFlags.forEach((flag, index) => {
        const position = String(index + 1)
        for (const questionId of flag.questionIds) {
            if (!questionIds.has(questionId)) {
                issues.push({
                    field: 'flags',
                    message: `La bandera de conteo ${position} referencia una pregunta inexistente («${questionId}»).`,
                })
            }
        }
        if (!levelIds.has(flag.levelId)) {
            issues.push({
                field: 'flags',
                message: `La bandera de conteo ${position} referencia un nivel inexistente («${flag.levelId}»).`,
            })
        }
        if (!Number.isInteger(flag.eachMinValue) || flag.eachMinValue < 1) {
            issues.push({
                field: 'flags',
                message: `El valor mínimo de la bandera de conteo ${position} debe ser un entero ≥ 1.`,
            })
        }
        if (
            !Number.isInteger(flag.countAtLeast) ||
            flag.countAtLeast < 1 ||
            flag.countAtLeast > flag.questionIds.length
        ) {
            issues.push({
                field: 'flags',
                message:
                    `El conteo mínimo de la bandera de conteo ${position} debe estar entre 1 y ` +
                    `la cantidad de preguntas que agrupa (${String(flag.questionIds.length)}).`,
            })
        }
    })
}

function validateAccessReferences(
    form: FormDoc,
    published: PublishedDirectory,
    issues: ValidationIssue[],
): void {
    const municipalityIds = new Set(form.municipalities.map((municipality) => municipality.id))
    form.levels.forEach((level, index) => {
        const position = String(index + 1)
        for (const lineId of level.lineIds) {
            if (!published.lineIds.has(lineId)) {
                const name = published.lineNames.get(lineId) ?? lineId
                issues.push({
                    field: 'levels',
                    message: `El nivel ${position} referencia la línea «${name}», que no está publicada.`,
                })
            }
        }
        const agencyRefs = [...level.agencyIds, ...Object.values(level.municipalAgencyIds).flat()]
        for (const agencyId of agencyRefs) {
            if (!published.agencyIds.has(agencyId)) {
                const name = published.agencyNames.get(agencyId) ?? agencyId
                issues.push({
                    field: 'levels',
                    message: `El nivel ${position} referencia la entidad «${name}», que no está publicada.`,
                })
            }
        }
        for (const municipalityId of Object.keys(level.municipalAgencyIds)) {
            if (!municipalityIds.has(municipalityId)) {
                issues.push({
                    field: 'levels',
                    message: `El nivel ${position} tiene accesos para un municipio inexistente («${municipalityId}»).`,
                })
            }
        }
    })
}

/**
 * La app elige el formulario VIVO de mayor `version`
 * (FirestoreQuestionnaireRepository.kt): un borrador cuya versión no supere la
 * publicada más alta no regiría al publicarse — o empataría, que es ambiguo.
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
