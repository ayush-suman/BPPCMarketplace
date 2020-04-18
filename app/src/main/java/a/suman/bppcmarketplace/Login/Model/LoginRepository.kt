package a.suman.bppcmarketplace.Login.Model

import a.suman.bppcmarketplace.R
import android.app.Application
import android.content.Intent
import android.preference.PreferenceManager
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException

class LoginRepository(application: Application) {
    private val RC_SIGN_IN = 1
    lateinit private var mGoogleSignInClient: GoogleSignInClient
    private var compositeDisposable: CompositeDisposable = CompositeDisposable()
    private lateinit var googleSignIntoken: String
    private var backendTokenMutableLiveData = MutableLiveData<String>()
    private var loginExceptionMutableLiveData = MutableLiveData<String>()

    fun getGoogleSignInIntent(): Intent {

        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(application.getString(R.string.OAuth2_client_id))
                .requestEmail().requestProfile().requestScopes(
                    Scope(Scopes.EMAIL), Scope(Scopes.PROFILE)
                )
                .build()

        mGoogleSignInClient = GoogleSignIn.getClient(application.applicationContext, gso)

        val signInIntent = mGoogleSignInClient.signInIntent
        Log.i(TAG, "getting intent")
        return signInIntent
    }

    var application: Application

    init {
        this.application = application
    }


    fun googleSignInComplete(data: Intent?) {
        try {
            Log.i(TAG, "Account recieved in repo")
            val completedTask = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = completedTask.getResult(ApiException::class.java)
            if (account != null) {
                Log.i(Companion.TAG, "${account.displayName} ${account.email}")
                googleSignIntoken = account.idToken.toString()
                val observable = RetrofitClient.instance!!.api.authWithBackend(googleSignIntoken)

                compositeDisposable.add(
                    observable.subscribeOn(Schedulers.io()).observeOn(
                        AndroidSchedulers.mainThread()
                    ).subscribe({ it ->
                        Log.i(TAG, "\n ${it.token}")
                        storeDataIntoSharedPref(TOKEN_TAG, it.token, application)
                        storeDataIntoSharedPref(EMAIL_TAG, it.email, application)
                        storeDataIntoSharedPref(USERNAME_TAG, it.username, application)
                        backendTokenMutableLiveData.postValue(it.token)
                    }
                        , {
                            if (it is HttpException) {
                                Log.d(TAG, "HTTPException ${it.message()}")
                            }
                        })
                )
            }


        } catch (e: ApiException) {
            loginExceptionMutableLiveData.postValue("Login Failed")
            Log.i(TAG, e.toString())

        }

    }

    fun getloginExceptionLiveData(): MutableLiveData<String> {
        return loginExceptionMutableLiveData
    }

    fun postTokenFromShared() {
        backendTokenMutableLiveData.postValue(getDataFromSharedPref(TOKEN_TAG, application))
    }


    fun getBackendTokenMutableLiveData(): MutableLiveData<String> {
        return backendTokenMutableLiveData
    }

    fun getTokenFromShared() {
        getDataFromSharedPref(TOKEN_TAG, application)
    }

    fun getEmailFromShared() {
        getDataFromSharedPref(EMAIL_TAG, application)
    }

    fun getUsernameFromShared() {
        getDataFromSharedPref(USERNAME_TAG, application)
    }

    companion object {
        const val TAG: String = "LoginRepository"
        const val TOKEN_TAG: String = "token"
        const val USERNAME_TAG: String = "username"
        const val EMAIL_TAG: String = "email"
    }

    private fun storeDataIntoSharedPref(
        key: String?,
        value: String?,
        application: Application
    ) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        val editor = preferences.edit()
        editor.putString(key, value)
        editor.commit()
        Log.i(TAG + "ShPref", "Data Saved")
    }


    private fun getDataFromSharedPref(
        key: String?,
        application: Application
    ): String? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        return preferences.getString(key, null)
    }

    fun clearDisposables() {
        compositeDisposable.clear()
    }

}