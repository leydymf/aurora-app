package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.core.security.LocalAccountDataEraser
import javax.inject.Inject

/**
 * El blob de credenciales muere con la cuenta (RF-20; caso 4 del ciclo de vida
 * de P-31 opción C). El store ya destruye su alias del Keystore ANTES que las
 * prefs (cripto-borrado), el orden que exige el contrato de los erasers.
 */
class StoredCredentialsEraser @Inject constructor(
    private val credentials: CredentialStore
) : LocalAccountDataEraser {
    override suspend fun erase() {
        credentials.erase()
    }
}
