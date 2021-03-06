package com.ft.ftchinese.ui.login

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.ft.ftchinese.R
import com.ft.ftchinese.databinding.FragmentSignUpBinding
import com.ft.ftchinese.viewmodel.Result
import com.ft.ftchinese.ui.base.*
import com.ft.ftchinese.model.reader.Credentials
import com.ft.ftchinese.store.SessionManager
import com.ft.ftchinese.store.TokenManager
import com.ft.ftchinese.viewmodel.LoginViewModel
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.support.v4.toast

/**
 * Hosted both in [LoginActivity] and [LinkFtcActivity]
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class SignUpFragment : ScopedFragment(),
        AnkoLogger {

    private var email: String? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenManager: TokenManager
    private lateinit var viewModel: LoginViewModel
    private lateinit var binding: FragmentSignUpBinding

    override fun onAttach(context: Context) {
        super.onAttach(context)

        sessionManager = SessionManager.getInstance(context)
        tokenManager = TokenManager.getInstance(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            email = it.getString(ARG_EMAIL)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_sign_up, container, false)

        binding.email = email
        binding.passwordInput.requestFocus()

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProvider(this)
                    .get(LoginViewModel::class.java)
        } ?: throw Exception("Invalid Exception")

        viewModel.loginFormState.observe(viewLifecycleOwner, Observer {
            val signUpState = it ?: return@Observer

            binding.signUpBtn.isEnabled = signUpState.isPasswordValid

            if (signUpState.error != null) {
                binding.passwordInput.error = getString(signUpState.error)
                binding.passwordInput.requestFocus()
            }
        })

        binding.passwordInput.afterTextChanged {
            viewModel.passwordDataChanged(binding.passwordInput.text.toString().trim())
        }

        // TODO: handle wechat sign-up.
        binding.signUpBtn.setOnClickListener {
            if (context?.isConnected != true) {
                toast(R.string.prompt_no_network)
                return@setOnClickListener
            }

            val e = email ?: return@setOnClickListener

            if (binding.passwordInput.text.toString().trim().isEmpty()) {
                binding.passwordInput.error = getString(R.string.error_invalid_password)
                return@setOnClickListener
            }

            binding.enableInput = false
            viewModel.inProgress.value = true

            val c = Credentials(
                email = e,
                password = binding.passwordInput.text.toString().trim(),
                deviceToken = tokenManager.getToken()
            )

            val account = sessionManager.loadAccount()

            // If account exists, this should be wechat signup.
            if (account == null) {
                viewModel.signUp(c)
            } else {
                if (account.isWxOnly) {
                    account.unionId?.let {
                        viewModel.wxSignUp(c, it)
                    }
                }
            }
        }

        viewModel.accountResult.observe(viewLifecycleOwner, {
            binding.enableInput = it !is Result.Success
        })
    }

    companion object {

        @JvmStatic
        fun newInstance(email: String) = SignUpFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_EMAIL, email)
            }
        }
    }
}
