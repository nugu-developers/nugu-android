package com.skt.nugu.sdk.platform.android.ux.template.presenter

import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.gson.Gson
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import org.json.JSONObject
import java.lang.ref.WeakReference

class TemplateRenderer(
    private val nuguClientProvider: NuguClientProvider,
    deviceTypeCode: String,
    fragmentManager: FragmentManager? = null,
    private val containerId: Int
) : DisplayAggregatorInterface.Renderer {

    companion object {
        private const val TAG = "TemplateRenderer"
        internal var SERVER_URL: String? = null
        internal var DEVICE_TYPE_CODE = "device_type_code"
    }

    interface NuguClientProvider {
        fun getNuguClient(): NuguAndroidClient
    }

    /**
     * The other Renderer that show any view associated with template.
     * For example if you show media notification by play status independently, it needs to be inform to TemplateRenderer.
     * Implement this interface and return templateId list being shown.
     */
    interface ExternalViewRenderer {
        class ViewInfo(val templateId: String)

        fun getVisibleList(): List<ViewInfo>?
    }

    private var fragmentManagerRef = WeakReference(fragmentManager)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val gson = Gson()

    fun setFragmentManager(fragmentManager: FragmentManager?) {
        fragmentManagerRef.clear()
        fragmentManagerRef = WeakReference(fragmentManager)
    }

    var externalViewRenderer: ExternalViewRenderer? = null

    init {
        DEVICE_TYPE_CODE = deviceTypeCode

        //Empty lyrics presenter for receiving lyrics. Actual lyrics control works in each TemplateView.
        nuguClientProvider.getNuguClient().audioPlayerAgent?.setLyricsPresenter(EmptyLyricsPresenter)
    }

    @Keep
    private class TemplatePayload(
        val playServiceId: String? = null
    )

    override fun render(
        templateId: String,
        templateType: String,
        templateContent: String,
        header: Header,
        displayType: DisplayAggregatorInterface.Type
    ): Boolean {
        Logger.i(
            TAG,
            "render() templateId:$templateId, \n templateType:$templateType, \n templateContent:$templateContent, \n header:$header, \n displayType$displayType"
        )

        if (fragmentManagerRef.get() == null) {
            Logger.d(TAG, "render() return false; fragmentManager is null")
            return false
        }

        mainHandler.post {
            val templateContentWithType = insertType(templateContent, templateType)

            val playServiceId = gson.fromJson<TemplatePayload>(templateContent, TemplatePayload::class.java)?.playServiceId ?: ""

            fragmentManagerRef.get()?.beginTransaction()?.run {
                setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .add(
                        containerId,
                        TemplateFragment.newInstance(
                            nuguProvider = nuguClientProvider,
                            externalRenderer = externalViewRenderer,
                            name = templateType,
                            dialogRequestId = header.dialogRequestId,
                            templateId = templateId,
                            template = templateContentWithType,
                            displayType = displayType.name,
                            playServiceId = playServiceId
                        ).apply {
                            showLyricAtFirst =
                                fragmentManagerRef.get()?.fragments?.any {
                                    it is TemplateFragment && it.getPlayServiceId() == playServiceId && it.isLyricsShowing()
                                } ?: false
                        }.also { newFragment ->
                            onNewTemplate(newFragment)
                        },
                        displayType.name
                    )
                    .commitNowAllowingStateLoss()
            }
        }

        return true
    }

    override fun clear(templateId: String, force: Boolean) {
        Logger.i(TAG, "clear() $templateId, $force")

        fragmentManagerRef.get()?.fragments?.find { it is TemplateFragment && it.getTemplateId() == templateId }
            ?.let { foundFragment ->
                mainHandler.post {
                    (foundFragment as TemplateFragment).close()
                }
            }
    }

    override fun update(templateId: String, templateContent: String) {
        Logger.i(TAG, "update() $templateId, $templateContent")

        fragmentManagerRef.get()?.fragments?.find { it is TemplateFragment && it.getTemplateId() == templateId }
            ?.let { foundFragment ->
                mainHandler.post {
                    (foundFragment as TemplateFragment).update(templateContent)
                }
            }
    }

    fun clearAll(): Boolean {
        var clearCnt = 0

        fragmentManagerRef.get()?.fragments?.filter { it != null && it is TemplateFragment }.run {
            clearCnt = this!!.size

            this.forEach {
                (it as TemplateFragment).close()
            }
        }

        return (clearCnt > 0).also { Logger.i(TAG, "clearAll(). $clearCnt template cleared ") }
    }

    private fun onNewTemplate(newFragment: Fragment) {
        if ((newFragment as? TemplateFragment)?.isMediaTemplate() == true) {
            fragmentManagerRef.get()?.fragments?.find { (it as? TemplateFragment)?.isMediaTemplate() == true }
                ?.run {
                    Logger.i(TAG, "clear previous media template ${(this as TemplateFragment).getTemplateId()}")
                    this.close()
                }
        }
    }

    //todo. How long should we use the logic below
    private fun insertType(content: String, type: String): String {
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

    fun setServerUrl(url: String? = null) {
        SERVER_URL = url
    }
}