package com.skt.nugu.sampleapp.provider

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.skt.nugu.sdk.core.interfaces.capability.sound.SoundProvider
import java.net.URI

/**
 * Default implementation of [SoundProvider] for content URIs for sounds.
 */
class FallbackSoundProvider(val context: Context) : SoundProvider {
    /**
     * Get the content:// style URI for the beep sound on the given content uri.
     * @param beepName the beep name
     */
    override fun getContentUri(beepName: String): URI {
        val resourceId : Int
        when (beepName) {
            "FAIL" -> {
                resourceId = com.skt.nugu.sampleapp.R.raw.beep_fail_01
            }
            else -> {
                return URI("")
            }
        }

        val resources = context.resources
        return URI(Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(resourceId))
                .appendPath(resources.getResourceTypeName(resourceId))
                .appendPath(resources.getResourceEntryName(resourceId))
                .build().toString())
    }
}