package com.skt.nugu.sdk.platform.android.ux.template.presenter

import android.os.Handler
import android.os.Looper
import android.support.annotation.Keep
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import com.google.gson.Gson
import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import org.json.JSONObject
import java.lang.ref.WeakReference

class TemplateRenderer(
    private val nuguClientProvider: NuguClientProvider,
    deviceTypeCode: String,
    fragmentManager: FragmentManager,
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

    private val fragmentManagerRef = WeakReference(fragmentManager)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val gson = Gson()

    init {
        DEVICE_TYPE_CODE = deviceTypeCode
        //Empty lyrics presenter for receiving lyrics. Actual lyrics control works in each TemplateView.
        nuguClientProvider.getNuguClient().audioPlayerAgent?.setLyricsPresenter(object : LyricsPresenter {
            override fun getVisibility(): Boolean {
                return false
            }

            override fun show(): Boolean {
                return false
            }

            override fun hide(): Boolean {
                return false
            }

            override fun controlPage(direction: Direction): Boolean {
                return false
            }
        })
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

        mainHandler.post {
            val templateContentWithType = insertType(templateContent, templateType)

            val playServiceId = gson.fromJson<TemplatePayload>(templateContent, TemplatePayload::class.java)?.playServiceId ?: ""
            var isReload = false

            // Reload logic is for performance. It seems not need in case of mobile poc.
//            fragmentManagerRef.get()?.fragments
//                ?.find { it is TemplateFragment && it.getDisplayType() == displayType.name && it.getPlayServiceId() == playServiceId }
//                ?.run {
//                    (this as TemplateFragment).run {
//                        isReload = true
//
//                        getNuguClient()?.getDisplay()?.displayCardCleared(getTemplateId())
//                        arguments = TemplateFragment.createBundle(templateType,
//                            header.dialogRequestId,
//                            templateId,
//                            templateContentWithType,
//                            displayType.name,
//                            playServiceId)
//
//                        reload(templateContentWithType)
//                    }
//                }

            if (!isReload) {
                fragmentManagerRef.get()?.beginTransaction()?.run {
                    setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(
                            containerId,
                            TemplateFragment.newInstance(
                                nuguProvider = nuguClientProvider,
                                name = templateType,
                                dialogRequestId = header.dialogRequestId,
                                templateId = templateId,
                                template = templateContentWithType,
                                displayType = displayType.name,
                                playServiceId = playServiceId
                            ).also { newFragment ->
                                onNewTemplate(newFragment)
                            },
                            displayType.name
                        )
                        .commitNowAllowingStateLoss()
                }
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