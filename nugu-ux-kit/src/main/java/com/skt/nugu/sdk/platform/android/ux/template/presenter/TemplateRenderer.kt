package com.skt.nugu.sdk.platform.android.ux.template.presenter

import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.audioplayer.playlist.OnPlaylistListener
import com.skt.nugu.sdk.agent.audioplayer.playlist.Playlist
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.agent.display.DisplayInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.view.media.playlist.*
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.*
import kotlin.concurrent.schedule

open class TemplateRenderer(
    protected val nuguClientProvider: NuguClientProvider,
    deviceTypeCode: String,
    fragmentManager: FragmentManager? = null,
    protected val containerId: Int
) : DisplayAggregatorInterface.Renderer, PlaylistRenderer, PlaylistEventListener {

    companion object {
        private const val TAG = "TemplateRenderer"
        internal var SERVER_URL: String? = null
        internal var DEVICE_TYPE_CODE = "device_type_code"
        private var TEMPLATE_REMOVE_DELAY_MS = 500L
    }

    interface NuguClientProvider {
        fun getNuguClient(): NuguAndroidClient
    }

    interface TemplateLoadingListener {
        fun onStart(templateId: String, templateType: String, displayType: DisplayAggregatorInterface.Type?) {}
        fun onComplete(templateId: String, templateType: String, displayType: DisplayAggregatorInterface.Type?) {}

        /**
         * While template loading if there is any kind of error received this callback will be called.
         * It can be called multiple times if various error received.
         * It can be called even if loading continues and complete.
         */
        fun onReceivedError(templateId: String, templateType: String, displayType: DisplayAggregatorInterface.Type?, errorDescription: String?) {}
    }

    /**
     * The other Renderer that show View related with template.
     * For example if you show media notification, it needs to inform to TemplateRenderer.
     * Implement this interface and return templateId list being shown.
     */
    interface ExternalViewRenderer {
        class ViewInfo(val templateId: String)

        fun getVisibleList(): List<ViewInfo>?
    }

    var templateLoadingListener: TemplateLoadingListener? = null
    var externalViewRenderer: ExternalViewRenderer? = null
    var templateHandlerFactory: TemplateHandler.TemplateHandlerFactory? = null

    private var fragmentManagerRef = WeakReference(fragmentManager)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val templateClearTimer by lazy { Timer() }

    protected val gson = Gson()
    private val defaultTemplateHandlerFactory = TemplateHandler.TemplateHandlerFactory()

    private var onPlaylistListener : OnPlaylistListener? = object : OnPlaylistListener {
        override fun onSetPlaylist(playlist: Playlist) {
            notifyNewPlaylistData(playlist)
        }

        override fun onUpdatePlaylist(changes: JsonObject, updated: Playlist) {
            notifyPlaylistDataUpdated(changes, updated)
        }

        override fun onClearPlaylist() {
            notifyPlaylistDataCleared()
        }
    }

    private val fragmentCallback = object : FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            Logger.d(TAG, "fragment destroy . current mediaTemplate cnt :${getMediaTemplateCount()}")
            if (getMediaTemplateCount() == 0) hidePlaylist()

            if (f is PlaylistFragment) {
                fm.fragments.filterIsInstance<PlaylistStateListener>().forEach {
                    it.onPlaylistHidden()
                }
            }
            super.onFragmentDestroyed(fm, f)
        }
    }

    init {
        DEVICE_TYPE_CODE = deviceTypeCode

        nuguClientProvider.getNuguClient().audioPlayerAgent?.run {
            // Empty lyrics presenter for receiving lyrics. Actual lyrics control works in each TemplateView.
            setLyricsPresenter(EmptyLyricsPresenter)
            onPlaylistListener?.apply(::addOnPlaylistListener)
        }

        if (fragmentManager != null) {
            fragmentManagerRef.get()?.registerFragmentLifecycleCallbacks(fragmentCallback, false)
        }
    }

    @Keep
    protected class TemplatePayload(
        val playServiceId: String? = null,
    )

    override fun render(
        templateId: String,
        templateType: String,
        templateContent: String,
        header: Header,
        displayType: DisplayAggregatorInterface.Type,
        parentTemplateId: String?,
    ): Boolean {
        Logger.i(
            TAG,
            "render() templateId:$templateId, \n templateType:$templateType, \n templateContent:$templateContent, \n header:$header, \n displayType:$displayType, \n parentTemplateId:$parentTemplateId"
        )

        if (fragmentManagerRef.get() == null) {
            Logger.d(TAG, "render() return false; fragmentManager is null")
            return false
        }

        mainHandler.post {
            val templateContentWithType = insertType(templateContent, templateType)

            val playServiceId = runCatching { gson.fromJson(templateContent, TemplatePayload::class.java) }.getOrNull()?.playServiceId ?: ""

            val isListSupportTemplate = getPlaylist() != null

            val previousSameServiceRenderInfo =
                (fragmentManagerRef.get()?.fragments?.find { it is TemplateFragment && it.getPlayServiceId() == playServiceId && it.getRenderInfo() != null } as? TemplateFragment)?.getRenderInfo()

            Logger.d(TAG, "render() isListSupportTemplate : $isListSupportTemplate, previousSameServiceRenderInfo : $previousSameServiceRenderInfo ")

            fragmentManagerRef.get()?.beginTransaction()?.run {
                val newTemplate = TemplateFragment.newInstance(
                    nuguProvider = nuguClientProvider,
                    externalRenderer = externalViewRenderer,
                    templateLoadingListener = templateLoadingListener,
                    templateHandlerFactory = templateHandlerFactory ?: defaultTemplateHandlerFactory,
                    name = templateType,
                    dialogRequestId = header.dialogRequestId,
                    templateId = templateId,
                    parentTemplateId = parentTemplateId,
                    template = templateContentWithType,
                    displayType = displayType,
                    playServiceId = playServiceId,
                    isPlaylistSupport = isListSupportTemplate,
                    playlistRenderer = this@TemplateRenderer
                )

                newTemplate.previousRenderInfo = previousSameServiceRenderInfo

                setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out).add(containerId, newTemplate, displayType.name).apply {
                    // todo. when different playlist.. even it is same service
                    if (newTemplate.isMediaTemplate()) {
                        // bring PlaylistFragment to front when  same service mediaTemplate coming up.
                        if (previousSameServiceRenderInfo != null) {
                            fragmentManagerRef.get()?.fragments?.find { it is PlaylistFragment }?.let { playlistFragment ->
                                detach(playlistFragment).attach(playlistFragment)
                            }
                        } else {
                            fragmentManagerRef.get()?.fragments?.find { it is PlaylistFragment }?.let { playlistFragment ->
                                remove(playlistFragment)
                            }
                        }
                    }
                }.commitNowAllowingStateLoss()

                onNewTemplate(newTemplate)
            }
        }

        return true
    }

    override fun clear(templateId: String, force: Boolean) {
        Logger.i(TAG, "clear() $templateId, $force")

        templateClearTimer.schedule(TEMPLATE_REMOVE_DELAY_MS) {
            fragmentManagerRef.get()?.fragments?.find { it is TemplateFragment && it.getTemplateId() == templateId }?.let { foundFragment ->
                mainHandler.post {
                    (foundFragment as TemplateFragment).close()
                }
            }
        }
    }

    override fun update(templateId: String, templateContent: String) {
        Logger.i(TAG, "update() $templateId, $templateContent")

        fragmentManagerRef.get()?.fragments?.find { it is TemplateFragment && it.getTemplateId() == templateId }?.let { foundFragment ->
            mainHandler.post {
                (foundFragment as TemplateFragment).update(templateContent)
            }
        }
    }

    open fun clearAll(): Boolean {
        var clearCnt: Int

        fragmentManagerRef.get()?.fragments?.filter { it != null && it is TemplateFragment }.run {
            clearCnt = this!!.size

            forEach {
                (it as TemplateFragment).close()
            }
        }

        hidePlaylist()

        return (clearCnt > 0).also { Logger.i(TAG, "clearAll(). $clearCnt template cleared ") }
    }

    open fun onNewTemplate(newFragment: Fragment) {
        if ((newFragment as? TemplateFragment)?.isMediaTemplate() == true) {
            templateClearTimer.schedule(TEMPLATE_REMOVE_DELAY_MS) {
                fragmentManagerRef.get()?.fragments?.find { it != newFragment && (it as? TemplateFragment)?.isMediaTemplate() == true }?.run {
                    Logger.i(TAG, "clear previous media template ${(this as TemplateFragment).getTemplateId()}")
                    close()
                }
            }
        }
    }

    override fun showPlaylist(): Boolean {
        if (fragmentManagerRef.get()?.findFragmentByTag(PlaylistFragment.TAG) != null) {
            Logger.d(TAG, "showPlayList(). already playlist shown. ")
            return false
        }

        mainHandler.post {
            fragmentManagerRef.get()?.beginTransaction()?.run {
                val playlistFragment = PlaylistFragment.newInstance(getPlaylist(), this@TemplateRenderer)

                playlistFragment.observeEditMode {
                    notifyPlaylistEditModeChanged(it)
                }

                playlistFragment.playlistBottomMargin = getPlaylistBottomMargin()

                setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out).add(containerId, playlistFragment, PlaylistFragment.TAG)
                    .commitNowAllowingStateLoss()
            }
        }

        return true
    }

    override fun hidePlaylist(): Boolean {
        Logger.d(TAG, "hidePlaylist")

        fragmentManagerRef.get()?.let { fragmentManager ->
            fragmentManager.findFragmentByTag(PlaylistFragment.TAG)?.let { playlistFragment ->
                mainHandler.post {
                    fragmentManager.beginTransaction().remove(playlistFragment).commitAllowingStateLoss()
                }
                return true
            }
        }

        return false
    }

    override fun setElementSelected(token: String, postback: String?, callback: DisplayInterface.OnElementSelectedCallback?) {
        (fragmentManagerRef.get()?.fragments?.find { (it as? TemplateFragment)?.isMediaTemplate() == true } as? TemplateFragment)?.getTemplateId()
            ?.let { templateId ->
                nuguClientProvider.getNuguClient().audioPlayerAgent?.setElementSelected(templateId, token, postback, callback)
            }

    }

    override fun modifyPlaylist(deletedTokens: List<String>, tokens: List<String>) {
        nuguClientProvider.getNuguClient().audioPlayerAgent?.modifyPlaylist(deletedTokens, tokens)
    }

    override fun isPlaylistVisible(): Boolean {
        return fragmentManagerRef.get()?.fragments?.any { it is PlaylistFragment } == true
    }

    private fun getMediaTemplateCount(): Int {
        return fragmentManagerRef.get()?.fragments?.count { (it as? TemplateFragment)?.isMediaTemplate() == true } ?: 0
    }

    private fun notifyPlaylistEditModeChanged(editMode: Boolean) {
        fragmentManagerRef.get()?.fragments?.filterIsInstance<PlaylistStateListener>()?.forEach {
            it.onPlaylistEditModeChanged(editMode)
        }
    }

    private fun notifyNewPlaylistData(playlist: Playlist) {
        fragmentManagerRef.get()?.fragments?.filterIsInstance<PlaylistDataListener>()?.forEach {
            it.onSetPlaylist(playlist)
        }
    }

    private fun notifyPlaylistDataUpdated(changes: JsonObject, updated: Playlist) {
        fragmentManagerRef.get()?.fragments?.filterIsInstance<PlaylistDataListener>()?.forEach {
            it.onUpdatePlaylist(changes, updated)
        }
    }

    private fun notifyPlaylistDataCleared() {
        fragmentManagerRef.get()?.fragments?.filterIsInstance<PlaylistDataListener>()?.forEach {
            it.onClearPlaylist()
        }

        hidePlaylist()
    }

    private fun getPlaylistBottomMargin(): Int {
        return (fragmentManagerRef.get()?.fragments?.find { (it as? TemplateFragment)?.isMediaTemplate() == true } as? TemplateFragment)?.getPlaylistBottomMargin()
            ?: 0
    }

    private fun getPlaylist(): Playlist? {
        return nuguClientProvider.getNuguClient().audioPlayerAgent?.getPlaylist()
    }

    //todo. How long should we use the logic below
    open fun insertType(content: String, type: String): String {
        val newType = when (type.contains(other = ".", ignoreCase = true)) {
            true -> type
            false -> "Display.$type"
        }

        try {
            return JSONObject(content).put("type", newType).toString()
        } catch (e: Throwable) {
        }

        return content
    }

    fun setFragmentManager(fragmentManager: FragmentManager?) {
        if (fragmentManager != null) {
            runCatching {
                fragmentManagerRef.get()?.unregisterFragmentLifecycleCallbacks(fragmentCallback)
            }

            fragmentManagerRef.clear()
            fragmentManagerRef = WeakReference(fragmentManager)
            fragmentManagerRef.get()?.registerFragmentLifecycleCallbacks(fragmentCallback, false)
        } else {
            runCatching {
                fragmentManagerRef.get()?.unregisterFragmentLifecycleCallbacks(fragmentCallback)
            }

            fragmentManagerRef.clear()
        }
    }

    fun setServerUrl(url: String? = null) {
        SERVER_URL = url
    }

    fun setTemplateRemoveDelay(delay: Long) {
        TEMPLATE_REMOVE_DELAY_MS = delay
    }

    fun onDestroyed() {
        onPlaylistListener?.run {
            nuguClientProvider.getNuguClient().audioPlayerAgent?.removeOnPlaylistListener(this)
        }
        onPlaylistListener = null

        fragmentManagerRef.get()?.unregisterFragmentLifecycleCallbacks(fragmentCallback)
        fragmentManagerRef.clear()

        templateClearTimer.cancel()

        externalViewRenderer = null
        templateLoadingListener = null
        templateHandlerFactory = null
    }
}