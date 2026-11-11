package com.creativeam.goorno.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class DriverAuthUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isSignedIn: Boolean = false
)

object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"
    private const val PREFS_NAME = "goorno_google_auth_prefs"
    private const val KEY_UID = "google_user_uid"
    private const val KEY_EMAIL = "google_user_email"
    private const val KEY_NAME = "google_user_name"
    private const val KEY_PHOTO = "google_user_photo"
    private const val KEY_SIGNED_IN = "google_user_signed_in"

    // Web Client ID gerado pelo Firebase Authentication do projeto goorno-oficial
    const val WEB_CLIENT_ID = "882187108549-gimrtr2p10gs08rko62nh66pejgnm407.apps.googleusercontent.com"

    private val _currentUserState = MutableStateFlow(DriverAuthUser())
    val currentUserState: StateFlow<DriverAuthUser> = _currentUserState.asStateFlow()

    fun init(context: Context) {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase init safely bypassed: ${t.message}")
        }

        val cached = try { getCachedUser(context) } catch (t: Throwable) { DriverAuthUser() }
        val firebaseUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase not yet initialized or offline: ${t.message}")
            null
        }

        if (firebaseUser != null) {
            val user = DriverAuthUser(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: cached.email,
                displayName = firebaseUser.displayName ?: cached.displayName,
                photoUrl = firebaseUser.photoUrl?.toString() ?: cached.photoUrl,
                isSignedIn = true
            )
            saveCachedUser(context, user)
            _currentUserState.value = user
        } else {
            _currentUserState.value = cached
        }
    }

    fun getCachedUser(context: Context): DriverAuthUser {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isSignedIn = prefs.getBoolean(KEY_SIGNED_IN, false)
        return DriverAuthUser(
            uid = prefs.getString(KEY_UID, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            displayName = prefs.getString(KEY_NAME, "") ?: "",
            photoUrl = prefs.getString(KEY_PHOTO, "") ?: "",
            isSignedIn = isSignedIn
        )
    }

    private fun saveCachedUser(context: Context, user: DriverAuthUser) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_UID, user.uid)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_NAME, user.displayName)
            .putString(KEY_PHOTO, user.photoUrl)
            .putBoolean(KEY_SIGNED_IN, user.isSignedIn)
            .apply()
    }

    suspend fun signInWithGoogle(activity: Activity): Result<DriverAuthUser> = withContext(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(activity)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                // Autenticação com Firebase
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                val fbUser: FirebaseUser? = authResult.user

                if (fbUser != null) {
                    val driverUser = DriverAuthUser(
                        uid = fbUser.uid,
                        email = fbUser.email ?: googleIdTokenCredential.id,
                        displayName = fbUser.displayName ?: googleIdTokenCredential.displayName ?: "Motorista GoOrNo",
                        photoUrl = fbUser.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString() ?: "",
                        isSignedIn = true
                    )

                    saveCachedUser(activity, driverUser)
                    _currentUserState.value = driverUser
                    Log.i(TAG, "Login com Google realizado com sucesso: ${driverUser.email}")
                    Result.success(driverUser)
                } else {
                    Result.failure(Exception("Não foi possível obter os dados do usuário."))
                }
            } else {
                Result.failure(Exception("Credencial recebida inválida ou não suportada."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Login cancelado pelo usuário.")
            Result.failure(Exception("Login cancelado."))
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao autenticar com Google: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            FirebaseAuth.getInstance().signOut()
            val credentialManager = CredentialManager.create(context)
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {}

            val emptyUser = DriverAuthUser()
            saveCachedUser(context, emptyUser)
            _currentUserState.value = emptyUser
            Log.i(TAG, "Usuário deslogado do Google.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deslogar: ${e.message}", e)
        }
    }
}
