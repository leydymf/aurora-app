package com.auroraclima.app.accounts.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.model.PasswordChecks
import com.auroraclima.app.core.ui.v2.RuleRow

// Reglas de la contraseña de la cuenta bajo el campo, visibles desde el inicio.
@Composable
internal fun PasswordRulesList(checks: PasswordChecks, modifier: Modifier = Modifier) {
    RuleRow(
        text = stringResource(R.string.account_password_rule_length, PasswordChecks.MIN_LENGTH),
        satisfied = checks.hasMinLength,
        modifier = modifier.padding(top = 10.dp)
    )
}
