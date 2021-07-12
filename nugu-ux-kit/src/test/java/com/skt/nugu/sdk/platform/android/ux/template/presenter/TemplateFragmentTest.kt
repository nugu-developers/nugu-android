package com.skt.nugu.sdk.platform.android.ux.template.presenter

import android.os.Build
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import kotlinx.coroutines.MainScope
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
@Config(sdk = [Build.VERSION_CODES.O_MR1])
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
    private val template = "{\"duration\":\"SHORT\",\"playStackControl\":{\"type\":\"PUSH\",\"playServiceId\":\"nugu.builtin.dictionary\"},\"supportVisibleTokenList\":false,\"supportFocusedItemToken\":false,\"grammarGuide\":[\"일본어로 엄마가 뭐야?\",\"중국어 사전에서 오리 찾아줘\",\"영어로 가죽이 뭐야?\",\"고루는 영어로\",\"영어로 행운을 빈다가 뭐야?\",\"각양각색은 영어로\",\"일본어 사전에서 사과 찾아줘\",\"가느다랗다는 영어로\",\"영어로 햇빛이 뭐야?\",\"중국어로 오늘 날씨 어때요가 뭐야?\",\"가지런히는 영어로\",\"영어로 까치가 뭐야?\",\"바다는 영어로 뭐야?\",\"영어로 경쟁이 뭐야?\",\"영어로 나 지금 배고파가 뭐야?\",\"가루는 영어로\",\"고르다는 영어로\",\"황제가 중국어로 뭐야?\",\"일본어로 지금 몇 시에요가 뭐야?\"],\"title\":{\"logo\":{\"sources\":[{\"url\":\"https://cdn.sktnugu.com/aladdin/image/play/dictionary/dictionary_logo_60_line.png\"}]},\"text\":{\"text\":\"어학사전\"}},\"playServiceId\":\"nugu.builtin.dictionary\",\"content\":{\"header\":{\"text\":\"사과\"},\"body\":{\"text\":\"apple\\napology\\nbeg pardon\"}},\"token\":\"127d0118e1019addef238d93048217bd\"}t"
    private val displayType = DisplayAggregatorInterface.Type.INFORMATION
    private val playServiceId = "news"

    private val nuguAndroidProvider = object : TemplateRenderer.NuguClientProvider {
        override fun getNuguClient(): NuguAndroidClient = nuguAndroidClient
    }

    private fun launchFragment(isMedia: Boolean = false): FragmentScenario<TemplateFragment> {
        val bundle = TemplateFragment.createBundle(if (isMedia) templateType_media else templateType,
            dialogRequestId,
            templateId,
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
            assertEquals(fragment.getTemplateType(), templateType)
            assertEquals(fragment.getDisplayType(), displayType)
            assertEquals(fragment.getDialogRequestedId(), dialogRequestId)
            assertEquals(fragment.getPlayServiceId(), playServiceId)
        }
    }

    @Test
    fun test2CloseButtonVisibility() {
        // default setting. test close button visible
        launchFragment().onFragment {
            onView(withId(R.id.btn_bar_close)).check(matches(isDisplayed()))
        }

        // launch media template fragment. and test close button not visible
        launchFragment(true).onFragment {
            withId(R.id.btn_bar_close).matches(doesNotExist())
        }

        // set all template not have close button, and test it's work
        TemplateView.enableCloseButton = { templateType, serviceID, displayType ->
            false
        }

        launchFragment().onFragment {
            withId(R.id.btn_bar_close).matches(doesNotExist())
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
    fun test4WebView(){
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