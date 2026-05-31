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

    init {
        try {
            if (auth?.currentUser != null) {
                _authState.value = AuthState.Authenticated
            } else if (auth == null) {
                _authState.value = AuthState.Error("Firebase not configured. Please add secrets.")
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
                _authState.value = AuthState.Authenticated
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
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Signup failed")
            }
        }
    }

    fun logout() {
        try {
            auth?.signOut()
            _authState.value = AuthState.Idle
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to logout")
        }
    }
}

