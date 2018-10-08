package com.ft.ftchinese.user

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ft.ftchinese.BuildConfig

import com.ft.ftchinese.R
import com.ft.ftchinese.models.Membership
import com.ft.ftchinese.models.SessionManager
import com.ft.ftchinese.models.User
import com.ft.ftchinese.util.RequestCode
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.android.synthetic.main.fragment_membership.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.toast

class MembershipActivity : SingleFragmentActivity() {
    override fun createFragment(): Fragment {
        return MembershipFragment.newInstance()
    }

    companion object {
        fun start(context: Context?) {
            val intent = Intent(context, MembershipActivity::class.java)
            context?.startActivity(intent)
        }
    }
}
/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [MembershipFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [MembershipFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class MembershipFragment : Fragment(), AnkoLogger {
    private var mListener: OnFragmentInteractionListener? = null
    private var mUser: User? = null
    private var wxApi: IWXAPI? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            mListener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wxApi = WXAPIFactory.createWXAPI(context, BuildConfig.WECAHT_APP_ID)

        info("onCreate finished")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_membership, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = try {
            requireContext()
        } catch (e: Exception) {
            return
        }
        mUser = SessionManager.getInstance(ctx).loadUser()

        // User is not logged in
        if (mUser == null) {
            membership_container.visibility = View.GONE

            paywall_login_button.setOnClickListener {
                SignInOrUpActivity.startForResult(activity, RequestCode.SIGN_IN)
            }

            subscribe_standard_year.setOnClickListener {
                SignInOrUpActivity.startForResult(activity, RequestCode.SIGN_IN)
            }

            subscription_premium_year.setOnClickListener {
                SignInOrUpActivity.startForResult(activity, RequestCode.SIGN_IN)
            }
        } else {
            // User is logged in
            // Show mUser's membership information


            // Hide login button
            paywall_login_container.visibility = View.GONE

            // Launch payment activity
            subscribe_standard_year.setOnClickListener {

                PaymentActivity.startForResult(activity, RequestCode.PAYMENT, Membership.TIER_STANDARD, Membership.BILLING_YEARLY)
            }

            subscription_premium_year.setOnClickListener {

                PaymentActivity.startForResult(activity, RequestCode.PAYMENT, Membership.TIER_PREMIUM, Membership.BILLING_YEARLY)
            }
        }
    }

    private fun updateUI() {
        member_value.text = when (mUser?.membership?.tier) {
            Membership.TIER_FREE -> getString(R.string.member_type_free)
            Membership.TIER_STANDARD -> getString(R.string.member_type_standard)
            Membership.TIER_PREMIUM -> getString(R.string.member_type_premium)
            else -> null
        }

        // Show expiration date
        duration_value.text = mUser?.membership?.localizedExpireDate
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RequestCode.PAYMENT) {
            if (resultCode != Activity.RESULT_OK) {
                toast("支付失败")
                return
            }

            // Get memberTier and billingCycle from intent, then update mUser, update ui.
            updateUI()
            toast("支付成功")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of MembershipFragment.
         */
        @JvmStatic
        fun newInstance() = MembershipFragment()
    }
}
