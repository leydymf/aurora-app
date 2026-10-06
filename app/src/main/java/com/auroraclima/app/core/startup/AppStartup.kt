package com.auroraclima.app.core.startup

/**
 * Ejecuta las tareas de arranque conteniendo cualquier fallo (ADR 2026-10-05,
 * apartado (d); auditoría A-2 del cierre pre-merge). Dos fallos quedan
 * contenidos: la CONSTRUCCIÓN del conjunto —que arrastra servicios que pueden no
 * existir en el dispositivo, como Firebase sin Play Services— y una tarea que
 * lance al correr. Sin esto, la excepción escapa del `launch` de la aplicación y
 * mata el proceso: una app de clima que no arranca justo después de una
 * eliminación interrumpida sería una anomalía observable (RNF-05), y la marca
 * durable de RF-20 ya garantiza que lo pendiente se reintente en el siguiente
 * arranque. Silenciosa a propósito: nada se registra en logs (regla 4 de
 * CLAUDE.md).
 *
 * Atrapa `Throwable` deliberadamente —sobrevivir al arranque pesa más que fallar
 * ruidosamente— y, única excepción documentada a la regla de `core/network`, NO
 * relanza `CancellationException`: el scope es el `SupervisorJob` de
 * `Application`, que nunca se cancela. Si el arranque pasa algún día a un scope
 * cancelable, hay que relanzarla.
 */
suspend fun runStartupTasks(initializers: () -> Set<AppInitializer>) {
    val tasks = runCatching(initializers).getOrNull() ?: return
    tasks.forEach { task -> runCatching { task.initialize() } }
}
