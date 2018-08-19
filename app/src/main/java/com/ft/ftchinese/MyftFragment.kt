package com.ft.ftchinese

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.ft.ftchinese.models.ChannelItem
import com.ft.ftchinese.models.Following
import com.ft.ftchinese.models.MyftTab
import kotlinx.android.synthetic.main.fragment_myft.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.find
import org.jetbrains.anko.info


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_TAB_ID = "tab_id"

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [MyftFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [MyftFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class MyftFragment : Fragment(), AnkoLogger {
    // TODO: Rename and change keys of parameters
    private var tabId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabId = it.getInt(ARG_TAB_ID)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_myft, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (tabId) {
            MyftTab.READING_HISTORY, MyftTab.STARRED_ARTICLE -> {
                initArticles()
            }

            MyftTab.FOLLOWING -> {
                initFollowing()
            }
        }
    }

    private fun initFollowing() {
        val tags = Following.loadAsList(context)

        recycler_view.apply {
            layoutManager = GridLayoutManager(context, 3)

            if (tags != null) {
                adapter = FollowingAdapter(tags)
            } else {
                Toast.makeText(context, "You have not followed anything", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private  fun initArticles() {
        recycler_view.apply {
            layoutManager = LinearLayoutManager(context)

            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    companion object {

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param tabId Identify which tab is selected.
         * @return A new instance of fragment MyftFragment.
         */
        // TODO: Rename and change keys and number of parameters
        @JvmStatic
        fun newInstance(tabId: Int) =
                MyftFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_TAB_ID, tabId)
                    }
                }
    }

    inner class FollowingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tagText: TextView? = itemView.findViewById(R.id.tag_text)
    }

    inner class FollowingAdapter(val items: MutableList<Following>) : RecyclerView.Adapter<FollowingViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowingViewHolder {

            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.following_item, parent, false)
            return FollowingViewHolder(view)
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: FollowingViewHolder, position: Int) {
            val item = items[position]

            holder.tagText?.text = item.tag
        }


        override fun onViewAttachedToWindow(holder: FollowingViewHolder) {
            super.onViewAttachedToWindow(holder)

            info("View attached to window: $holder")
        }
    }

    inner class MyArticleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = itemView.findViewById(R.id.title_text)
        val standfirstText: TextView = itemView.findViewById(R.id.standfirst_text)
    }

    inner class MyArticleAdapter(val items: List<ChannelItem>) : RecyclerView.Adapter<MyArticleViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyArticleViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.my_article_item, parent, false)
            return MyArticleViewHolder(view)
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: MyArticleViewHolder, position: Int) {
            val item = items[position]
            holder.titleText.text = item.headline
            holder.standfirstText.text = item.shortlead
        }

    }
}
