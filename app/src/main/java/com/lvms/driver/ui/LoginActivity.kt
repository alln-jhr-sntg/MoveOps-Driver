package com.lvms.driver.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.lvms.driver.databinding.ActivityLoginBinding
import com.lvms.driver.model.LoginRequest
import com.lvms.driver.model.LoginResponse
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.AuthApi
import com.lvms.driver.network.SessionManager
import com.lvms.driver.network.parseError
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authApi by lazy { ApiClient.retrofit.create(AuthApi::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyInsetPadding(top = true, bottom = true, includeIme = true)

        SessionManager.init(applicationContext)
        if (SessionManager.isLoggedIn()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }
        binding.loginButton.setOnClickListener {
            attemptLogin()
        }
    }

    private fun attemptLogin() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            binding.statusText.text = "Email and password are required"
            return
        }

        setLoading(true)

        authApi.login("auth/login", LoginRequest(email, password))
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    setLoading(false)
                    val body = response.body()

                    if (response.isSuccessful && body != null) {
                        SessionManager.saveSession(
                            token = body.token,
                            userId = body.userId,
                            firstName = body.firstName,
                            lastName = body.lastName
                        )
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    } else {
                        binding.statusText.text = response.parseError()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    setLoading(false)
                    binding.statusText.text = "Connection failed: ${t.message}"
                }
            })
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !isLoading
        if (isLoading) binding.statusText.text = ""
    }
}