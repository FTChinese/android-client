package com.ft.ftchinese

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class ChangeEmailActivity : SingleFragmentActivity() {
    override fun createFragment(): Fragment {
        return ChangeEmailFragment.newInstance()
    }

}

class ChangeEmailFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.fragment_change_email, container, false)
    }

    companion object {
        fun newInstance(): ChangePasswordFragment {
            return ChangePasswordFragment()
        }
    }
}