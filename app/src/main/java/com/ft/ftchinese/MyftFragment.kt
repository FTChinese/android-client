package com.ft.ftchinese

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.ft.ftchinese.database.ArticleCursorWrapper
import com.ft.ftchinese.database.ArticleStore
import com.ft.ftchinese.models.ChannelItem
import com.ft.ftchinese.models.MyftTab
import kotlinx.android.synthetic.main.fragment_recycler.*
import kotlinx.coroutines.experimental.Job
import org.jetbrains.anko.AnkoLogger


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
    private var job: Job? = null
    private var cursor: ArticleCursorWrapper? = null
    private var mCursorAdapter: CursorAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabId = it.getInt(ARG_TAB_ID)
        }

        val ctx = context ?: return
        when (tabId) {
            MyftTab.READING_HISTORY -> {
                cursor = ArticleStore.getInstance(ctx).queryHistory()
            }
            MyftTab.STARRED_ARTICLE -> {
                cursor = ArticleStore.getInstance(ctx).queryStarred()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_recycler, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler_view.layoutManager = LinearLayoutManager(context)

        updateUI()
    }

    private fun updateUI() {

        val c = cursor ?: return
        if (mCursorAdapter == null) {
            mCursorAdapter = CursorAdapter(c)
            recycler_view.adapter = mCursorAdapter
        } else {
            mCursorAdapter?.setCursor(c)
            mCursorAdapter?.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        job?.cancel()
        cursor?.close()
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

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val primaryText: TextView = itemView.findViewById(R.id.primary_text_view)
        val secondaryText: TextView = itemView.findViewById(R.id.secondary_text_view)
    }

    inner class CursorAdapter(var mCursor: ArticleCursorWrapper) : RecyclerView.Adapter<ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_primary_secondary, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return mCursor.count
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            mCursor.moveToPosition(position)
            val item = mCursor.loadItem()

            holder.primaryText.text = item.headline
            holder.secondaryText.text = item.standfirst

            holder.itemView.setOnClickListener {
                StoryActivity.start(context, item)
            }
        }

        fun setCursor(cursor: ArticleCursorWrapper) {
            if (!mCursor.isClosed) {
                mCursor.close()
            }
            mCursor = cursor
        }
    }
}
