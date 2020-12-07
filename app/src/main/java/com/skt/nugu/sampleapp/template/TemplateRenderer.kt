package com.skt.nugu.sampleapp.template

import android.os.Handler
import android.os.Looper
import android.support.v4.app.Fragment
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

        mainHandler.post {
            val templateContentWithType = insertType(templateContent, templateType)

            val playServiceId = gson.fromJson<TemplatePayload>(templateContent, TemplatePayload::class.java)?.playServiceId ?: ""
            var isReload = false

            fragmentManagerRef.get()?.fragments
                ?.find { it is TemplateFragment && it.getDisplayType() == displayType.name && it.getPlayServiceId() == playServiceId }
                ?.run {
                    (this as TemplateFragment).run {
                        isReload = true

                        ClientManager.getClient().getDisplay()?.displayCardCleared(getTemplateId())
                        arguments = TemplateFragment.createBundle(templateType,
                            header.dialogRequestId,
                            templateId,
                            templateContentWithType,
                            displayType.name,
                            playServiceId)

                        reload(templateContentWithType)
                    }
                }

            if (!isReload) {
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
                            ).also { newFragment ->
                                onNewTemplate(newFragment)
                            },
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
                    (foundFragment as TemplateFragment).close()
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

    private fun onNewTemplate(newFragment: Fragment) {
        if ((newFragment as? TemplateFragment)?.isMediaTemplate() == true) {
            fragmentManagerRef.get()?.fragments?.find { (it as? TemplateFragment)?.isMediaTemplate() == true }
                ?.run {
                    Log.i(TAG, "clear previous media template ${(this as TemplateFragment).getTemplateId()}")
                    clear((this as TemplateFragment).getTemplateId(), true)
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

}