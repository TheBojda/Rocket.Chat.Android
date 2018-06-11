package chat.rocket.android.createchannel.ui

import android.os.Bundle
import android.support.design.chip.Chip
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import chat.rocket.android.R
import chat.rocket.android.createchannel.presentation.CreateChannelPresenter
import chat.rocket.android.createchannel.presentation.CreateChannelView
import chat.rocket.android.main.ui.MainActivity
import chat.rocket.android.members.adapter.MembersAdapter
import chat.rocket.android.members.viewmodel.MemberViewModel
import chat.rocket.android.util.extensions.asObservable
import chat.rocket.android.util.extensions.inflate
import chat.rocket.android.util.extensions.showToast
import chat.rocket.android.util.extensions.ui
import chat.rocket.android.widget.DividerItemDecoration
import chat.rocket.common.model.RoomType
import dagger.android.support.AndroidSupportInjection
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_create_channel.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CreateChannelFragment : Fragment(), CreateChannelView, ActionMode.Callback {
    @Inject
    lateinit var createChannelPresenter: CreateChannelPresenter
    private var actionMode: ActionMode? = null
    private val adapter: MembersAdapter = MembersAdapter {
        if (it.username != null) {
            processSelectedMember(it.username)
        }
    }
    private val compositeDisposable = CompositeDisposable()
    private var channelType: RoomType = RoomType.CHANNEL
    private var isChannelReadOnly: Boolean = false
    private var memberList = arrayListOf<String>()

    companion object {
        fun newInstance() = CreateChannelFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidSupportInjection.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_create_channel)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolBar()
        setupViewListeners()
        setupRecyclerView()
        subscribeEditTexts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unsubscribeEditTexts()
    }

    override fun showLoading() {
        ui {
            view_loading.isVisible = true
        }
    }

    override fun hideLoading() {
        ui {
            view_loading.isVisible = false
        }
    }

    override fun showMessage(resId: Int) {
        ui {
            showToast(resId)
        }
    }

    override fun showMessage(message: String) {
        ui {
            showToast(message)
        }
    }

    override fun showGenericErrorMessage() {
        showMessage(getString(R.string.msg_generic_error))
    }

    override fun showUserSuggestion(dataSet: List<MemberViewModel>) {
        // Hiding the progress because we are showing it already.
        hideSuggestionViewInProgress()
        if (dataSet.isEmpty()) {
            showNoSuggestionView()
        } else {
            showSuggestionViewResults(dataSet)
        }
    }

    override fun prepareToShowChatList() {
        with(activity as MainActivity) {
            setCheckedNavDrawerItem(R.id.action_chat_rooms)
            openDrawer()
            getDrawerLayout().postDelayed(600) {
                closeDrawer()
                createChannelPresenter.toChatList()
            }
        }
    }

    override fun showChannelCreatedSuccessfullyMessage() {
        showMessage(getString(R.string.msg_channel_created_successfully))
    }

    override fun enableUserInput() {
        text_channel_name.isEnabled = true
        text_invite_members.isEnabled = true
    }

    override fun disableUserInput() {
        text_channel_name.isEnabled = false
        text_invite_members.isEnabled = false
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.create_channel, menu)
        mode.title = getString(R.string.title_create_channel)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_create_channel -> {
                createChannelPresenter.createChannel(
                    channelType,
                    text_channel_name.text.toString(),
                    memberList,
                    isChannelReadOnly
                )
                mode.finish()
                true
            }
            else -> {
                false
            }
        }
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
    }

    private fun setupToolBar() {
        (activity as AppCompatActivity?)?.supportActionBar?.title =
                getString(R.string.title_create_channel)
    }

    private fun setupViewListeners() {
        switch_channel_type.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                text_channel_type.text = getString(R.string.msg_private_channel)
                text_channel_type_description.text =
                        getString(R.string.msg_private_channel_description)
                image_channel_icon.setImageDrawable(
                    context?.getDrawable(R.drawable.ic_lock_black_12_dp)
                )
                channelType = RoomType.PRIVATE_GROUP
            } else {
                text_channel_type.text = getString(R.string.msg_public_channel)
                text_channel_type_description.text =
                        getString(R.string.msg_public_channel_description)
                image_channel_icon.setImageDrawable(
                    context?.getDrawable(R.drawable.ic_hashtag_black_12dp)
                )
                channelType = RoomType.CHANNEL
            }
        }

        switch_read_only.setOnCheckedChangeListener { _, isChecked ->
            isChannelReadOnly = isChecked
        }
    }

    private fun setupRecyclerView() {
        ui {
            recycler_view.layoutManager =
                    LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            recycler_view.addItemDecoration(DividerItemDecoration(it))
            recycler_view.adapter = adapter

        }
    }

    private fun subscribeEditTexts() {
        val channelNameDisposable = text_channel_name.asObservable()
            .subscribe {
                if (it.isNotBlank()) {
                    startActionMode()
                } else {
                    finishActionMode()
                }
            }

        val inviteMembersDisposable = text_invite_members.asObservable()
            .debounce(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribe {
                if (it.length >= 3) {
                    showSuggestionViewInProgress()
                    createChannelPresenter.searchUser(it.toString())
                } else {
                    hideSuggestionView()
                }
            }

        compositeDisposable.addAll(channelNameDisposable, inviteMembersDisposable)
    }

    private fun unsubscribeEditTexts() {
        compositeDisposable.dispose()
    }

    private fun startActionMode() {
        if (actionMode == null) {
            actionMode = (activity as MainActivity).startSupportActionMode(this)
        }
    }

    private fun finishActionMode() {
        actionMode?.finish()
    }

    private fun processSelectedMember(username: String) {
        if (memberList.contains(username)) {
            showMessage(getString(R.string.msg_member_already_added))
        } else {
            hideSuggestionView()
            text_invite_members.setText("")
            addMember(username)
            addChip(username)
            chip_group_member.isVisible = true
        }
    }

    private fun addMember(username: String) {
        memberList.add(username)
    }

    private fun removeMember(username: String) {
        memberList.remove(username)
    }

    private fun addChip(chipText: String) {
        val chip = Chip(context)
        chip.chipText = chipText
        chip.isCloseIconEnabled = true
        chip.setChipBackgroundColorResource(R.color.icon_grey)
        setupChipOnCloseIconClickListener(chip)
        chip_group_member.addView(chip)
    }

    private fun setupChipOnCloseIconClickListener(chip: Chip) {
        chip.setOnCloseIconClickListener {
            removeChip(it)
            removeMember((it as Chip).chipText.toString())
            // whenever we remove a chip we should process the chip group visibility.
            processChipGroupVisibility()
        }
    }

    private fun removeChip(chip: View) {
        chip_group_member.removeView(chip)
    }

    private fun processChipGroupVisibility() {
        chip_group_member.isVisible = memberList.isNotEmpty()
    }

    private fun showSuggestionView() {
        view_member_suggestion.isVisible = true
    }

    private fun hideSuggestionView() {
        view_member_suggestion.isVisible = false
    }

    private fun showSuggestionViewInProgress() {
        recycler_view.isVisible = false
        text_member_not_found.isVisible = false
        view_member_suggestion_loading.isVisible = true
        showSuggestionView()
    }

    private fun hideSuggestionViewInProgress() {
        view_member_suggestion_loading.isVisible = false
    }

    private fun showSuggestionViewResults(dataSet: List<MemberViewModel>) {
        adapter.clearData()
        adapter.prependData(dataSet)
        text_member_not_found.isVisible = false
        recycler_view.isVisible = true
        showSuggestionView()
    }

    private fun showNoSuggestionView() {
        recycler_view.isVisible = false
        text_member_not_found.isVisible = true
        showSuggestionView()
    }
}