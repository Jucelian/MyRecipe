package com.example.myrecipe.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.myrecipe.R
import com.example.myrecipe.BuildConfig

@Composable
fun LoginScreen(authViewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var username by remember { mutableStateOf(authViewModel.savedUsername.value) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberBiometric by remember { mutableStateOf(authViewModel.isBiometricEnabled.value) }
    var isSignUp by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme
    val biometricHelper = remember { BiometricHelper(context) }

    val onAuthAction = {
        if (username.isBlank() || password.isBlank() || (isSignUp && email.isBlank())) {
            errorMessage = "Please fill in all fields"
        } else {
            isLoading = true
            errorMessage = null
            if (isSignUp) {
                authViewModel.signup(username, password, email) { success ->
                    if (success) {
                        if (rememberBiometric) authViewModel.setBiometricEnabled(true, password)
                        onLoginSuccess()
                    } else {
                        isLoading = false
                        errorMessage = "Signup failed. Try a different username."
                    }
                }
            } else {
                authViewModel.login(username, password) { success ->
                    if (success) {
                        if (rememberBiometric) authViewModel.setBiometricEnabled(true, password)
                        onLoginSuccess()
                    } else {
                        isLoading = false
                        errorMessage = "Invalid username or password"
                    }
                }
            }
        }
    }

    val onBiometricLogin = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            biometricHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    isLoading = true
                    authViewModel.biometricLogin { success ->
                        if (success) {
                            onLoginSuccess()
                        } else {
                            isLoading = false
                            errorMessage = "Biometric login failed"
                        }
                    }
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.secondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.chefmate_logo),
                    contentDescription = "ChefMate Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "RECIPE COLLECTION",
                color = colorScheme.onSecondary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignUp) "Create Account" else "Welcome Back!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground
                )
                Text(
                    text = if (isSignUp) "Sign up to start saving your favorite recipes." else "Sign in to access your saved recipes and favorites.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = colorScheme.surface,
                        focusedContainerColor = colorScheme.surface,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onAuthAction()
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = colorScheme.surface,
                        focusedContainerColor = colorScheme.surface,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = colorScheme.primary
                    )
                )

                if (isSignUp) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = TextFieldDefaults.colors(unfocusedContainerColor = colorScheme.surface, focusedContainerColor = colorScheme.surface, unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = colorScheme.primary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberBiometric,
                            onCheckedChange = { rememberBiometric = it }
                        )
                        Text(
                            text = "Biometric",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onBackground
                        )
                    }

                    if (!isSignUp) {
                        TextButton(onClick = { showResetDialog = true }) {
                            Text("Forgot Password?", style = MaterialTheme.typography.bodySmall, color = colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAuthAction,
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(if (isSignUp) "Sign Up" else "Sign In")
                        }
                    }

                    if (!isSignUp && authViewModel.canUseBiometricLogin()) {
                        IconButton(
                            onClick = onBiometricLogin,
                            modifier = Modifier
                                .size(50.dp)
                                .background(colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Login", tint = colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (isSignUp) "Already have an account? " else "Don't have an account? ", fontSize = 14.sp)
                    TextButton(onClick = { 
                        isSignUp = !isSignUp 
                        errorMessage = null
                    }) {
                        Text(text = if (isSignUp) "Sign In" else "Sign Up", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) "_Debug" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

    if (showResetDialog) {
        ResetPasswordDialog(
            authViewModel = authViewModel,
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
fun ResetPasswordDialog(authViewModel: AuthViewModel, onDismiss: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter your username and email to set a new password.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                message?.let {
                    Text(text = it, color = if (isError) MaterialTheme.colorScheme.error else Color.Green, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isLoading = true
                    authViewModel.resetPassword(username, email, newPassword) { success, msg ->
                        isLoading = false
                        message = msg
                        isError = !success
                        if (success) {
                            // Optionally auto-dismiss after delay
                        }
                    }
                },
                enabled = !isLoading && username.isNotBlank() && email.isNotBlank() && newPassword.isNotBlank()
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
