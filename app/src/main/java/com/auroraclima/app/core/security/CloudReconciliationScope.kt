package com.auroraclima.app.core.security

import javax.inject.Qualifier

/** Scope de las reconciliaciones que nadie espera (ADR 2026-10-06). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudReconciliationScope
