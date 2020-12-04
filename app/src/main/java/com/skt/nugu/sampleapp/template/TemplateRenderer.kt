package com.skt.nugu.sampleapp.template

import android.os.Handler
import android.os.Looper
import android.support.v4.app.FragmentManager
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.json.JSONObject
import java.lang.ref.WeakReference

class TemplateRenderer(
    fragmentManager: FragmentManager,
    private val containerId: Int
) : DisplayAggregatorInterface.Renderer {

    companion object {
        private const val TAG = "TemplateRenderer"
    }

    private val fragmentManagerRef = WeakReference(fragmentManager)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val gson = Gson()

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
        Log.i(
            TAG,
            "render() templateId:$templateId, \n templateType:$templateType, \n templateContent:$templateContent, \n header:$header, \n displayType$displayType"
        )

        fun insertType(template: String, type: String): String {
            try {
                return JSONObject(template).put("type", type).toString()
            } catch (e: Throwable) {
            }

            return template
        }

        mainHandler.post {
            //todo. How long should we use the logic below
            val templateContentWithType = insertType(
                templateContent,
                when (templateType.contains(other = ".", ignoreCase = true)) {
                    true -> templateType
                    false -> "Display.$templateType"
                }
            )

            val playServiceId = gson.fromJson<TemplatePayload>(templateContent, TemplatePayload::class.java)?.playServiceId ?: ""
            var update = false

            fragmentManagerRef.get()?.fragments
                ?.find { it is TemplateFragment && it.getDisplayType() == displayType.name && it.getPlayServiceId() == playServiceId }
                ?.run {
                    (this as TemplateFragment).run {
                        update = true

                        ClientManager.getClient().getDisplay()?.displayCardCleared(getTemplateId())
                        arguments = TemplateFragment.createBundle(templateType,
                            header.dialogRequestId,
                            templateId,
                            templateContentWithType,
                            displayType.name,
                            playServiceId)

                        reload(templateContentWithType)
                        ClientManager.getClient().getDisplay()?.displayCardRendered(templateId, null)
                    }
                }

            if (!update) {
                fragmentManagerRef.get()?.beginTransaction()?.run {
                    setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(
                            containerId,
                            TemplateFragment.newInstance(
                                name = templateType,
                                dialogRequestId = header.dialogRequestId,
                                templateId = templateId,
                                template = templateContentWithType,
                                displayType = displayType.name,
                                playServiceId = playServiceId
                            ),
                            displayType.name
                        )
                        .commitNowAllowingStateLoss()

                    ClientManager.getClient().getDisplay()?.displayCardRendered(templateId, null)
                }
            }
        }

        return true
    }

    override fun clear(templateId: String, force: Boolean) {
        Log.i(TAG, "clear() $templateId, $force")

        fragmentManagerRef.get()?.fragments?.find { it is TemplateFragment && it.getTemplateId() == templateId }
            ?.let { foundFragment ->
                mainHandler.post {
                    fragmentManagerRef.get()?.beginTransaction()?.remove(foundFragment)?.commitAllowingStateLoss()
                    ClientManager.getClient().getDisplay()?.displayCardCleared(templateId)
                }
            }
    }

    override fun update(templateId: String, templateContent: String) {
        Log.i(TAG, "update() $templateId, $templateContent")

        fragmentManagerRef.get()?.fragments?.find { it is TemplateFragment && it.getTemplateId() == templateId }
            ?.let { foundFragment ->
                (foundFragment as TemplateFragment).update(templateContent)
            }
    }

}