package com.example

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
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUserEmail = MutableStateFlow(auth?.currentUser?.email ?: "")
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

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
                        _authState.value = AuthState.Authenticated
                    } else {
                        _currentUserEmail.value = ""
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
                }
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Signup failed")
            }
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
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to logout")
        }
    }

    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth?.removeAuthStateListener(it) }
    }
}
