package chat.rocket.android.createchannel.presentation

import chat.rocket.android.core.lifecycle.CancelStrategy
import chat.rocket.android.main.presentation.MainNavigator
import chat.rocket.android.members.viewmodel.MemberViewModelMapper
import chat.rocket.android.server.domain.GetCurrentServerInteractor
import chat.rocket.android.server.infraestructure.RocketChatClientFactory
import chat.rocket.android.util.extensions.launchUI
import chat.rocket.common.RocketChatException
import chat.rocket.common.model.RoomType
import chat.rocket.common.util.ifNull
import chat.rocket.core.RocketChatClient
import chat.rocket.core.internal.rest.createChannel
import chat.rocket.core.internal.rest.searchUser
import javax.inject.Inject

class CreateChannelPresenter @Inject constructor(
    private val view: CreateChannelView,
    private val strategy: CancelStrategy,
    private val mapper: MemberViewModelMapper,
    private val navigator: MainNavigator,
    val serverInteractor: GetCurrentServerInteractor,
    val factory: RocketChatClientFactory
) {
    private val client: RocketChatClient = factory.create(serverInteractor.get()!!)

    fun createChannel(
        roomType: RoomType,
        channelName: String,
        usersList: List<String>,
        readOnly: Boolean
    ) {
        launchUI(strategy) {
            view.showLoading()
            view.disableUserInput()
            try {
                client.createChannel(roomType, channelName, usersList, readOnly)
                view.prepareToShowChatList()
                view.showChannelCreatedSuccessfullyMessage()
                toChatList()
            } catch (exception: RocketChatException) {
                exception.message?.let {
                    view.showMessage(it)
                }.ifNull {
                    view.showGenericErrorMessage()
                }
            } finally {
                view.hideLoading()
                view.enableUserInput()
            }
        }
    }

    fun searchUser(query: String) {
        launchUI(strategy) {
            try {
                val users = client.searchUser(query, count = 5)
                val memberViewModelMapper = mapper.mapToViewModelList(users.result)
                view.showUserSuggestion(memberViewModelMapper)
            } catch (ex: RocketChatException) {
                ex.message?.let {
                    view.showMessage(it)
                }.ifNull {
                    view.showGenericErrorMessage()
                }
            }
        }
    }

    fun toChatList() = navigator.toChatList()
}