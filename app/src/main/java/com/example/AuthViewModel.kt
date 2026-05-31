package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
        if (auth == null) {
            _authState.value = AuthState.Error("Firebase not configured. Please add secrets.")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth?.signInWithEmailAndPassword(email, pass)?.await()
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
        if (auth == null || db == null) {
            _authState.value = AuthState.Error("Firebase not configured. Please add secrets.")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val result = auth?.createUserWithEmailAndPassword(email, pass)?.await()
                result?.user?.let { user ->
                    val profile = hashMapOf(
                        "id" to user.uid,
                        "email" to email,
                        "full_name" to fullName,
                        "created_at" to System.currentTimeMillis()
                    )
                    db?.collection("profiles")?.document(user.uid)?.set(profile)?.await()
                }
                _currentUserEmail.value = auth?.currentUser?.email ?: email
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

    fun logout() {
        try {
            auth?.signOut()
            _currentUserEmail.value = ""
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
