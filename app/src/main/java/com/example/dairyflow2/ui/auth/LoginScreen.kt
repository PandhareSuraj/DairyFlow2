package com.example.dairyflow2.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.dairyflow2.core.UiState
import com.example.dairyflow2.data.model.UserRole

@Composable
fun LoginScreen(
    loginState: UiState<Unit>,
    onLogin: (String, String) -> Unit,
    onDemoLogin: (UserRole) -> Unit,
    onCreateAccount: (String, String, UserRole, String, String) -> Unit,
) {
    var isCreateMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("admin@dairyflow.local") }
    var password by rememberSaveable { mutableStateOf("password") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.Admin) }
    val isLoading = loginState is UiState.Loading
    val canSubmit = if (isCreateMode) {
        fullName.isNotBlank() && email.isNotBlank() && password.length >= 6
    } else {
        email.isNotBlank() && password.isNotBlank()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.LocalDrink, contentDescription = null, modifier = Modifier.height(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("DairyFlow", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                if (isCreateMode) "Create your operations account" else "Milk delivery operations",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            if (isCreateMode) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                AccountTypeSelector(role, onRole = { role = it })
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (isCreateMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (loginState is UiState.Error) {
                Spacer(Modifier.height(12.dp))
                Text(loginState.message, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (isCreateMode) {
                        onCreateAccount(email.trim(), password, role, fullName.trim(), phone.trim())
                    } else {
                        onLogin(email.trim(), password)
                    }
                },
                enabled = canSubmit && !isLoading,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(if (isCreateMode) Icons.Default.PersonAdd else Icons.Default.Login, contentDescription = null)
                Text(
                    when {
                        isLoading && isCreateMode -> "Creating account"
                        isLoading -> "Signing in"
                        isCreateMode -> "Create account"
                        else -> "Sign in"
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (!isCreateMode) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onDemoLogin(UserRole.Admin) }, enabled = !isLoading) {
                        Text("Demo admin")
                    }
                    OutlinedButton(onClick = { onDemoLogin(UserRole.DeliveryBoy) }, enabled = !isLoading) {
                        Text("Demo staff")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    isCreateMode = !isCreateMode
                    if (isCreateMode) {
                        email = ""
                        password = ""
                    }
                },
                enabled = !isLoading,
            ) {
                Text(if (isCreateMode) "Already have an account? Sign in" else "Create account")
            }
        }
    }
}

@Composable
private fun AccountTypeSelector(selected: UserRole, onRole: (UserRole) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        AccountTypeButton("Admin", selected == UserRole.Admin, onClick = { onRole(UserRole.Admin) }, modifier = Modifier.weight(1f))
        AccountTypeButton(
            "Delivery",
            selected == UserRole.DeliveryBoy,
            onClick = { onRole(UserRole.DeliveryBoy) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AccountTypeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        Button(onClick = onClick, shape = RoundedCornerShape(8.dp), modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp), modifier = modifier) {
            Text(label)
        }
    }
}
