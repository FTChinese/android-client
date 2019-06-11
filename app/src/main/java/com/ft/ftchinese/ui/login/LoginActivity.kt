package com.ft.ftchinese.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.ft.ftchinese.R
import com.ft.ftchinese.base.ScopedAppActivity
import com.ft.ftchinese.base.handleError
import com.ft.ftchinese.model.SessionManager
import com.ft.ftchinese.util.RequestCode
import kotlinx.android.synthetic.main.progress_bar.*
import kotlinx.android.synthetic.main.simple_toolbar.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.toast

@kotlinx.coroutines.ExperimentalCoroutinesApi
class LoginActivity : ScopedAppActivity(), AnkoLogger {

    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: LoginViewModel

    private fun showProgress(show: Boolean) {
        if (show) {
            progress_bar.visibility = View.VISIBLE
        } else {
            progress_bar.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_double)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
        }

        sessionManager = SessionManager.getInstance(this)

        viewModel = ViewModelProviders.of(this)
                .get(LoginViewModel::class.java)

        viewModel.emailResult.observe(this, Observer {
            val findResult = it ?:return@Observer

            if (findResult.error != null) {
                handleError(findResult.error)

                return@Observer
            }

            if (findResult.success == null) {
                return@Observer
            }

            val (email, found) = findResult.success
            if (found) {
                supportActionBar?.setTitle(R.string.title_login)

                supportFragmentManager.commit {
                    replace(R.id.double_frag_primary, SignInFragment.newInstance(email))
                    addToBackStack(null)
                }

            } else {
                supportActionBar?.setTitle(R.string.title_sign_up)

                supportFragmentManager.commit {
                    replace(R.id.double_frag_primary, SignUpFragment.newInstance(email, HOST_SIGN_UP_ACTIVITY))
                    addToBackStack(null)
                }
            }
        })

        viewModel.loginResult.observe(this, Observer {
            val loginResult = it ?: return@Observer

            if (loginResult.error != null) {
                toast(loginResult.error)
                return@Observer
            }

            if (loginResult.exception != null) {
                handleError(loginResult.exception)
                return@Observer
            }

            if (loginResult.success == null) {
                toast(R.string.error_not_loaded)
                return@Observer
            }

            sessionManager.saveAccount(loginResult.success)

            setResult(Activity.RESULT_OK)

            finish()
        })

        viewModel.inProgress.observe(this, Observer<Boolean> {
            showProgress(it)
        })

        supportFragmentManager.commit {
            replace(R.id.double_frag_primary, EmailFragment.newInstance())
            replace(R.id.double_frag_secondary, WxLoginFragment.newInstance())
        }
    }

    companion object {
        fun startForResult(activity: Activity?) {
            activity?.startActivityForResult(
                    Intent(activity, LoginActivity::class.java),
                    RequestCode.SIGN_IN
            )
        }
    }
}
