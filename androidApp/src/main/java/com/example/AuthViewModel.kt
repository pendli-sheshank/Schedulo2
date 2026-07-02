package com.example

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Patterns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class PasswordChangeState {
    object Idle : PasswordChangeState()
    object Loading : PasswordChangeState()
    object Success : PasswordChangeState()
    data class Error(val message: String) : PasswordChangeState()
}

sealed class ResetState {
    object Idle : ResetState()
    object Sent : ResetState()
    data class Error(val message: String) : ResetState()
}

sealed class DeleteAccountState {
    object Idle : DeleteAccountState()
    object Loading : DeleteAccountState()
    object NeedsReauth : DeleteAccountState()
    object Success : DeleteAccountState()
    data class Error(val message: String) : DeleteAccountState()
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }
    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance(), FIRESTORE_DB_NAME)
        } catch (e: Exception) {
            null
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUserEmail = MutableStateFlow(auth?.currentUser?.email ?: "")
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

    private val _currentUserId = MutableStateFlow(auth?.currentUser?.uid ?: "")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
        try {
            val firebaseAuth = auth
            if (firebaseAuth == null) {
                _authState.value = AuthState.Error("Firebase not configured. Please add secrets.")
            } else {
                authStateListener = FirebaseAuth.AuthStateListener { fireAuth ->
                    val user = fireAuth.currentUser
                    if (user != null) {
                        _currentUserEmail.value = user.email ?: ""
                        _currentUserId.value = user.uid
                        _authState.value = AuthState.Authenticated
                    } else {
                        _currentUserEmail.value = ""
                        _currentUserId.value = ""
                        if (_authState.value is AuthState.Authenticated) {
                            _authState.value = AuthState.Idle
                        }
                    }
                }
                firebaseAuth.addAuthStateListener(authStateListener!!)

                if (firebaseAuth.currentUser != null) {
                    _authState.value = AuthState.Authenticated
                    _currentUserEmail.value = firebaseAuth.currentUser?.email ?: ""
                }
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Unknown init error")
        }
    }

    fun login(email: String, pass: String) {
        val trimmedEmail = email.trim()
        if (auth == null) {
            _authState.value = AuthState.Error("Firebase not configured. Please add secrets.")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth?.signInWithEmailAndPassword(trimmedEmail, pass)?.await()
                val user = auth?.currentUser
                _currentUserEmail.value = user?.email ?: email
                _authState.value = AuthState.Authenticated
                ensureProfileExistsForUser(user?.uid, email)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun signup(email: String, pass: String, fullName: String) {
        val trimmedEmail = email.trim()
        val trimmedName = fullName.trim()
        if (auth == null || db == null) {
            _authState.value = AuthState.Error("Firebase not configured. Please add secrets.")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }
        if (pass.length < 8 || !pass.any { it.isLetter() } || !pass.any { it.isDigit() }) {
            _authState.value = AuthState.Error("Password must be at least 8 characters with letters and numbers.")
            return
        }
        if (trimmedName.isBlank() || trimmedName.length > 100) {
            _authState.value = AuthState.Error("Please enter a valid name (max 100 characters).")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val result = auth?.createUserWithEmailAndPassword(trimmedEmail, pass)?.await()
                result?.user?.let { user ->
                    val profile = hashMapOf(
                        "id" to user.uid,
                        "email" to trimmedEmail,
                        "full_name" to trimmedName,
                        "created_at" to System.currentTimeMillis()
                    )
                    db?.collection("profiles")?.document(user.uid)?.set(profile)?.await()
                    // Send a verification email so team features can be gated on a
                    // verified address (see isEmailVerified / createTeam/joinTeam).
                    try { user.sendEmailVerification().await() } catch (_: Exception) { }
                }
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Signup failed")
            }
        }
    }

    /** Whether the signed-in user has a verified email (team features are gated on this). */
    val isEmailVerified: Boolean
        get() = auth?.currentUser?.isEmailVerified == true

    /** Re-send the verification email to the current user. */
    fun resendVerificationEmail() {
        val user = auth?.currentUser ?: return
        viewModelScope.launch {
            try { user.sendEmailVerification().await() } catch (_: Exception) { }
        }
    }

    /** Refresh the cached user so isEmailVerified reflects a link the user just clicked. */
    fun refreshEmailVerification() {
        val user = auth?.currentUser ?: return
        viewModelScope.launch {
            try { user.reload().await() } catch (_: Exception) { }
        }
    }

    private suspend fun ensureProfileExistsForUser(uid: String?, email: String) {
        if (uid == null || db == null) return
        try {
            val doc = db?.collection("profiles")?.document(uid)?.get()?.await()
            if (doc == null || !doc.exists()) {
                val profile = hashMapOf(
                    "id" to uid,
                    "email" to email,
                    "full_name" to "",
                    "created_at" to System.currentTimeMillis()
                )
                db?.collection("profiles")?.document(uid)?.set(profile)?.await()
            } else {
                val storedEmail = doc.getString("email") ?: ""
                if (storedEmail != email && email.isNotBlank()) {
                    db?.collection("profiles")?.document(uid)?.update("email", email)?.await()
                }
            }
        } catch (_: Exception) { }
    }

    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()

    fun deleteAccount(password: String? = null) {
        val user = auth?.currentUser ?: run {
            _deleteState.value = DeleteAccountState.Error("No user signed in.")
            return
        }
        val uid = user.uid
        _deleteState.value = DeleteAccountState.Loading
        viewModelScope.launch {
            try {
                if (password != null && user.email != null) {
                    val credential = EmailAuthProvider.getCredential(user.email!!, password)
                    user.reauthenticate(credential).await()
                }
                val shiftsQuery = db?.collection("shifts")?.whereEqualTo("userId", uid)?.get()?.await()
                shiftsQuery?.documents?.forEach { it.reference.delete().await() }
                val jobsQuery = db?.collection("jobs")?.whereEqualTo("userId", uid)?.get()?.await()
                jobsQuery?.documents?.forEach { it.reference.delete().await() }
                val adjustmentsQuery = db?.collection("pay_adjustments")?.whereEqualTo("userId", uid)?.get()?.await()
                adjustmentsQuery?.documents?.forEach { it.reference.delete().await() }
                db?.collection("profiles")?.document(uid)?.delete()?.await()
                db?.collection("settings")?.document(uid)?.delete()?.await()
                user.delete().await()
                _deleteState.value = DeleteAccountState.Success
                _authState.value = AuthState.Idle
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                _deleteState.value = DeleteAccountState.NeedsReauth
            } catch (e: Exception) {
                _deleteState.value = DeleteAccountState.Error(e.message ?: "Failed to delete account.")
            }
        }
    }

    fun resetDeleteState() {
        _deleteState.value = DeleteAccountState.Idle
    }

    private val _passwordChangeState = MutableStateFlow<PasswordChangeState>(PasswordChangeState.Idle)
    val passwordChangeState: StateFlow<PasswordChangeState> = _passwordChangeState.asStateFlow()

    fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth?.currentUser ?: run {
            _passwordChangeState.value = PasswordChangeState.Error("No user signed in.")
            return
        }
        val email = user.email ?: run {
            _passwordChangeState.value = PasswordChangeState.Error("No email associated with account.")
            return
        }
        if (newPassword.length < 8 || !newPassword.any { it.isLetter() } || !newPassword.any { it.isDigit() }) {
            _passwordChangeState.value = PasswordChangeState.Error("New password must be at least 8 characters with letters and numbers.")
            return
        }
        _passwordChangeState.value = PasswordChangeState.Loading
        viewModelScope.launch {
            try {
                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                user.reauthenticate(credential).await()
                user.updatePassword(newPassword).await()
                _passwordChangeState.value = PasswordChangeState.Success
            } catch (e: Exception) {
                _passwordChangeState.value = PasswordChangeState.Error(e.message ?: "Failed to change password.")
            }
        }
    }

    fun resetPasswordChangeState() {
        _passwordChangeState.value = PasswordChangeState.Idle
    }

    private val _resetState = MutableStateFlow<ResetState>(ResetState.Idle)
    val resetState: StateFlow<ResetState> = _resetState.asStateFlow()

    fun sendPasswordReset(email: String) {
        val trimmedEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _resetState.value = ResetState.Error("Please enter a valid email address.")
            return
        }
        viewModelScope.launch {
            try {
                auth?.sendPasswordResetEmail(trimmedEmail)?.await()
                _resetState.value = ResetState.Sent
            } catch (e: Exception) {
                _resetState.value = ResetState.Error(e.message ?: "Failed to send reset email.")
            }
        }
    }

    fun resetResetState() {
        _resetState.value = ResetState.Idle
    }

    fun logout() {
        try {
            auth?.signOut()
            _authState.value = AuthState.Idle
            _biometricLockActive.value = false
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to logout")
        }
    }

    // --- Biometric Login ---

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    // True when the app cold-started with a persisted Firebase session and biometric
    // login is enabled, so the dashboard must stay gated until the prompt succeeds.
    private val _biometricLockActive = MutableStateFlow(false)
    val biometricLockActive: StateFlow<Boolean> = _biometricLockActive.asStateFlow()

    fun initBiometricPreference(context: Context) {
        val prefs = context.getSharedPreferences("schedulo_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("biometric_enabled", false)
        _biometricEnabled.value = enabled
        if (enabled && auth?.currentUser != null) {
            _biometricLockActive.value = true
        }
    }

    fun dismissBiometricLock() {
        _biometricLockActive.value = false
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("schedulo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _biometricEnabled.value = enabled
    }

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    _authState.value = AuthState.Error("Biometric error: $errString")
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Individual attempt failed; the system will allow retries automatically
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Login")
            .setSubtitle("Use your fingerprint or face to sign in")
            .setNegativeButtonText("Use password")
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }

    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth?.removeAuthStateListener(it) }
    }
}
