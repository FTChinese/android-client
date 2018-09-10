package com.ft.ftchinese.user

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ft.ftchinese.R
import com.ft.ftchinese.models.ErrorResponse
import com.ft.ftchinese.models.PasswordUpdate
import com.ft.ftchinese.models.User
import com.ft.ftchinese.util.gson
import kotlinx.android.synthetic.main.fragment_password.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.toast

internal class PasswordFragment : Fragment(), AnkoLogger {

    private var mUser: User? = null
    private var job: Job? = null
    private var mListener: OnFragmentInteractionListener? = null

    private var isInProgress: Boolean
        get() = !password_save_button.isEnabled
        set(value) {
            mListener?.onProgress(value)
        }

    private var isInputAllowed: Boolean
        get() = new_password.isEnabled
        set(value) {
            old_password.isEnabled = value
            new_password.isEnabled = value
            confirm_password.isEnabled = value
        }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            mListener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val userData = it.getString(ARG_USER_DATA)
            mUser = try {
                gson.fromJson<User>(userData, User::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.fragment_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        password_save_button.setOnClickListener {
            info("Saving password...")
            attemptSave()
        }
    }

    private fun attemptSave() {
        old_password.error = null
        new_password.error = null
        confirm_password.error = null

        val oldPassword = old_password.text.toString().trim()
        val newPassword = new_password.text.toString().trim()
        val confirmPassword = confirm_password.text.toString().trim()

        var cancel = false
        var focusView: View? = null

        if (oldPassword.isBlank()) {
            old_password.error = getString(R.string.error_invalid_password)
            focusView = old_password
            cancel = true
        }

        if (newPassword.isBlank()) {
            new_password.error = getString(R.string.error_invalid_password)
            focusView = new_password
            cancel = true
        }

        if (confirmPassword.isBlank()) {
            confirm_password.error = getString(R.string.error_invalid_password)
            focusView = confirm_password
            cancel = true
        }

        if (newPassword != confirmPassword) {
            confirm_password.error = getString(R.string.error_mismatched_confirm_password)
            focusView = confirm_password
            cancel = true
        }

        if (cancel) {
            focusView?.requestFocus()

            return
        }

        save(oldPassword, newPassword)
    }

    private fun save(oldPassword: String, newPassword: String) {

        val uuid = mUser?.id ?: return

        isInProgress = true
        isInputAllowed = false

        job = launch(UI) {
            val passwordUpdate = PasswordUpdate(oldPassword, newPassword)

            try {
                info("Start updating password")

                passwordUpdate.updateAsync(uuid).await()

                isInProgress = false
                toast(R.string.success_saved)
            } catch (e: ErrorResponse) {
                e.printStackTrace()
                isInProgress = false
                isInputAllowed = true

                when (e.statusCode) {
                    // Should handle duplicate email here.
                    400 -> {
                        toast("提交了非法的JSON")
                    }
                    422 -> {
                        toast("密码过长")
                    }
                    404 -> {
                        toast("用户不存在")
                    }
                    403 -> {
                        toast("当前密码未通过验证")
                    }
                }
            } catch (e: Exception) {
                isInProgress = false
                isInputAllowed = true

                handleException(e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    companion object {
        private const val ARG_USER_DATA = "user_data"
        fun newInstance(user: User?) = PasswordFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_USER_DATA, gson.toJson(mUser))
            }
        }
    }
}