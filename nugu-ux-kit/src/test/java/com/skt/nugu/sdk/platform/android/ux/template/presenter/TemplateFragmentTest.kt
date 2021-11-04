package com.skt.nugu.sdk.platform.android.ux.template.presenter

import android.os.Build
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TemplateFragmentTest {
    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    @Mock
    private lateinit var templateLoadingListener: TemplateRenderer.TemplateLoadingListener

    @Mock
    private lateinit var externalViewRenderer: TemplateRenderer.ExternalViewRenderer

    @Mock
    private lateinit var templateHandlerFactory: TemplateHandler.TemplateHandlerFactory

    private val templateType = "type_sample"
    private val templateType_media = "AudioPlayer.Template1"
    private val dialogRequestId = "abc"
    private val templateId = "123"
    private val parentTemplateId = "456"
    private val template = "template Content"
    private val displayType = DisplayAggregatorInterface.Type.INFORMATION
    private val playServiceId = "news"

    private val nuguAndroidProvider = object : TemplateRenderer.NuguClientProvider {
        override fun getNuguClient(): NuguAndroidClient = nuguAndroidClient
    }

    private fun launchFragment(isMedia: Boolean = false): FragmentScenario<TemplateFragment> {
        val bundle = TemplateFragment.createBundle(if (isMedia) templateType_media else templateType,
            dialogRequestId,
            templateId,
            parentTemplateId,
            template,
            displayType,
            playServiceId)

        return launchFragmentInContainer(bundle) {
            TemplateFragment.newInstance(nuguAndroidProvider,
                externalViewRenderer,
                templateLoadingListener,
                templateHandlerFactory,
                if (isMedia) templateType_media else templateType,
                dialogRequestId,
                templateId,
                parentTemplateId,
                template,
                displayType,
                playServiceId)
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun test1TemplateArgs() {
        launchFragment().onFragment { fragment ->
            assertEquals(fragment.getTemplateId(), templateId)
            assertEquals(fragment.getParentTemplateId(), parentTemplateId)
            assertEquals(fragment.getTemplateType(), templateType)
            assertEquals(fragment.getDisplayType(), displayType)
            assertEquals(fragment.getDialogRequestedId(), dialogRequestId)
            assertEquals(fragment.getPlayServiceId(), playServiceId)

            fragment.closeWithParents()
        }
    }

    @Test
    fun test3NuguButtonVisibility() {
        launchFragment(true).onFragment {
            assertEquals(it.isNuguButtonVisible(), false)
        }

        launchFragment().onFragment {
            assertEquals(it.isNuguButtonVisible(), true)
        }
    }

    @Test
    fun test4WebView() {
        launchFragment().onFragment {
            it.view?.post {
                onWebView()
                    .forceJavascriptEnabled()
//                    .withElement(findElement(Locator.CLASS_NAME, "grammar-icon blue"))  // nugu button
//                    .perform(webClick()) // Similar to perform(click())
            }

        }
    }
}