package com.skt.nugu.sdk.platform.android.ux.template.view.media

import android.os.Build
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.client.theme.ThemeManager
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.controller.DefaultTemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateAndroidHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.model.*
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateFragment
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.annotation.Config


@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class DisplayAudioPlayerTest {

    private val gson = Gson()

    private lateinit var audioPlayerViewType1: DisplayAudioPlayer
    private lateinit var audioPlayerViewType2: DisplayAudioPlayer

    @Mock
    private lateinit var themeManager: ThemeManager

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    @Mock
    private lateinit var nuguClientProvider: TemplateRenderer.NuguClientProvider

    @Mock
    private lateinit var templateInfo: TemplateHandler.TemplateInfo

    @Mock
    private lateinit var fragment: Fragment

    @InjectMocks
    private lateinit var emptyAudioPlayerInfo: AudioPlayer

    @Mock
    private lateinit var emptyAudioPlayerTitle: AudioPlayerTitle

    @Mock
    private lateinit var emptyAudioPlayerContent: AudioPlayerContent

    private lateinit var audioPlayerInfo: AudioPlayer

    private val audioPlayerTitle = AudioPlayerTitle("iconUrl", "text")

    @Mock
    private lateinit var templateLoadingListener: TemplateRenderer.TemplateLoadingListener

    @Mock
    private lateinit var externalViewRenderer: TemplateRenderer.ExternalViewRenderer

    private lateinit var templateHandlerSpy: TemplateHandler

    private val templateHandlerFactory = object : TemplateHandler.TemplateHandlerFactory() {
        override fun onCreate(
            nuguProvider: TemplateRenderer.NuguClientProvider,
            templateInfo: TemplateHandler.TemplateInfo,
            fragment: Fragment,
        ): TemplateHandler {
            templateHandlerSpy = spy(super.onCreate(nuguProvider, templateInfo, fragment))
            return templateHandlerSpy
        }
    }

    private var audioPlayerContent: AudioPlayerContent = AudioPlayerContent(
        "title",
        "subtitle1",
        "subtitle2",
        "imageUrl",
        "durationSec",
        "bgUrl",
        "bgColor",
        "badgeMessage",
        "badgeUrl",
        Lyrics("title", LyricsType.SYNC, null, ShowButtonText("showButtonText")),
        Settings(true, Repeat.ALL, false))

    private val templateTypeMedia = "AudioPlayer.Template1"
    private val dialogRequestId = "abc"
    private val templateId = "123"
    private val parentTemplateId = "456"
    private val template_dummy = "template Content"
    private val template_news =
        "{\"type\":\"AudioPlayer.Template1\",\"title\":{\"iconUrl\":\"https://news_logo.png\",\"text\":\"9월 6일 키워드 뉴스\",\"logoimage\":\"https://news_bg.png\"},\"grammarGuide\":[\"다음 뉴스 \",\"MBC 뉴스 들려줘\",\"CBS 뉴스 틀어줘\",\"이전 뉴스\",\"SBS 뉴스 틀어줘\",\"다시 들려줘\",\"TBS 뉴스 재생\",\"KBS 뉴스 재생\",\"정치 뉴스 재생해줘\",\"어제 스포츠 뉴스 틀어줘\",\"연합뉴스 들려줘\",\"지난주 부동산 뉴스 알려줘\",\"SK텔레콤 관련 뉴스 알려줘\",\"일시정지\",\"최근 뉴스 알려줘\",\"코로나 관련 뉴스 들려줘\",\"뉴스 들려줘\",\"오늘 연예 뉴스 알려줘\"],\"content\":{\"title\":\"[증시 키워드] 외국인, 지난주 삼성전자ㆍSK하이닉스 8568억 순매수\",\"subTitle\":\"\",\"subtitle1\":\"\",\"subtitle2\":\"\",\"imageUrl\":\"https://temp.png\",\"durationSec\":\"180\",\"lyrics\":{\"title\":\"[증시 키워드] 외국인, 지난주 삼성전자ㆍSK하이닉스 8568억 순매수\",\"lyricsType\":\"NON_SYNC\",\"lyricsInfoList\":[{\"text\":\"6일 국내 증시 키워드는 #삼성전자\"},{\"text\":\"#SK하이닉스 #네이버 #LG화학 #LG이노텍\"},{\"text\":\"등입니다.\"},{\"text\":\"특히 외국인은 지난주(8월 30일 ~ 9월 3일)\"},{\"text\":\"삼성전자와 SK하이닉스 8568억 원어치를\"},{\"text\":\"순매수했습니다.\"},{\"text\":\"외국인은 지난주 SK하이닉스도 2552억\"},{\"text\":\"원을 순매수했습니다.\"},{\"text\":\"외국인은 지난주 네이버를 3659억 원\"},{\"text\":\"순매수했습니다.\"}],\"showButton\":{\"text\":\"요약문 보기\"}}},\"playServiceId\":\"nugu.builtin.news\",\"playStackControl\":{\"playServiceId\":\"nugu.builtin.news\",\"type\":\"PUSH\"},\"sourceType\":\"ATTACHMENT\",\"token\":\"news-9월 6일 월요일-키워드-THEME\",\"url\":null}\n"

    private val template_pop =
        "{\"type\":\"AudioPlayer.Template1\",\"title\":{\"iconUrl\":\"https:line.png\",\"text\":\"Melon\"},\"content\":{\"title\":\"내 손을 잡아\",\"subtitle1\":\"아이유\",\"subtitle2\":\"최고의 사랑 OST Part.4\",\"imageUrl\":\"https:_500.jpg\\/melon\\/optimize\\/90\",\"durationSec\":\"195\",\"backgroundImageUrl\":\"https:6252_500.jpg\\/melon\\/optimize\\/90\",\"lyrics\":{\"title\":\"내 손을 잡아 · 아이유\",\"lyricsType\":\"SYNC\",\"lyricsInfoList\":[{\"time\":1,\"text\":\"내 손을 잡아 - 아이유\"},{\"time\":9122,\"text\":\"느낌이 오잖아 \"},{\"time\":11651,\"text\":\"떨리고 있잖아\"},{\"time\":13532,\"text\":\"언제까지 눈치만 볼 거니\"},{\"time\":19159,\"text\":\"네 맘을 말해봐 \"},{\"time\":21656,\"text\":\"딴청 피우지 말란 말이야\"},{\"time\":25096,\"text\":\"네 맘 가는 그대로 \"},{\"time\":28846,\"text\":\"지금 내 손을 잡아\"},{\"time\":33878,\"text\":\"어서 내 손을 잡아\"},{\"time\":41342,\"text\":\"우연히 고개를 돌릴 때 마다\"},{\"time\":46314,\"text\":\"눈이 마주치는 건\"},{\"time\":51287,\"text\":\"며칠밤 내내 꿈속에 나타나\"},{\"time\":55161,\"text\":\"밤새 나를 괴롭히는 건\"},{\"time\":61349,\"text\":\"그 많은 빈자리 중에서 하필\"},{\"time\":66225,\"text\":\"내 옆자릴 고르는 건\"},{\"time\":71290,\"text\":\"나도 모르게 어느새 실없는 웃음\"},{\"time\":76228,\"text\":\"흘리고 있다는 건\"},{\"time\":80429,\"text\":\"그럼 말 다했지 뭐\"},{\"time\":83003,\"text\":\"우리 얘기 좀 할까\"},{\"time\":85405,\"text\":\"느낌이 오잖아 떨리고 있잖아\"},{\"time\":89776,\"text\":\"언제까지 눈치만 볼 거니\"},{\"time\":95441,\"text\":\"네 맘을 말해봐 \"},{\"time\":97829,\"text\":\"딴청 피우지 말란 말이야\"},{\"time\":101326,\"text\":\"네 맘 가는 그대로 \"},{\"time\":104977,\"text\":\"지금 내 손을 잡아\"},{\"time\":110093,\"text\":\"핸드폰 진동에 \"},{\"time\":112531,\"text\":\"심장이 덜컥내려 앉는다는 건\"},{\"time\":120030,\"text\":\"오 나도 모르게 \"},{\"time\":121529,\"text\":\"어느새 짓궂은 네 말투\"},{\"time\":125091,\"text\":\"자꾸 듣고 싶은걸\"},{\"time\":129213,\"text\":\"어떡해\"},{\"time\":129899,\"text\":\"저기 멀리 걸어온다\"},{\"time\":133074,\"text\":\"눈이 마주친다\"},{\"time\":135079,\"text\":\"언제까지 넌 모른척 할거니\"},{\"time\":140392,\"text\":\"사랑이 온거야 너와 나 말이야\"},{\"time\":144840,\"text\":\"네가 좋아 정말 못 견딜 만큼\"},{\"time\":150495,\"text\":\"그거면 된거야 \"},{\"time\":152845,\"text\":\"더는 생각하지 말란 말이야\"},{\"time\":156267,\"text\":\"네 맘 가는 그대로\"},{\"time\":160494,\"text\":\"느낌이 오잖아 떨리고 있잖아\"},{\"time\":164768,\"text\":\"언제까지 눈치만 볼 거니\"},{\"time\":170333,\"text\":\"네 맘을 말해봐 \"},{\"time\":172884,\"text\":\"딴청 피우지 말란 말이야\"},{\"time\":176397,\"text\":\"네 맘 가는 그대로 \"},{\"time\":180095,\"text\":\"지금 내 손을 잡아\"},{\"time\":185102,\"text\":\"그냥 내 손을 잡아\"},{\"time\":190081,\"text\":\"지금 내 손을 잡아\"}]},\"settings\":{\"favorite\":true,\"repeat\":\"ALL\",\"shuffle\":false}},\"playServiceId\":\"nugu.builtin.music\",\"playStackControl\":{\"playServiceId\":\"nugu.builtin.music\",\"type\":\"PUSH\"},\"token\":\"token\",\"url\":\"https:\\/\\/D%3D\"}"
    private val displayType = DisplayAggregatorInterface.Type.INFORMATION
    private val playServiceId = "news"

    private val nuguAndroidProvider = object : TemplateRenderer.NuguClientProvider {
        override fun getNuguClient(): NuguAndroidClient = nuguAndroidClient
    }

    private fun launchFragment(templateString: String = template_dummy): FragmentScenario<TemplateFragment> {
        val bundle = TemplateFragment.createBundle(templateTypeMedia,
            dialogRequestId,
            templateId,
            parentTemplateId,
            templateString,
            displayType,
            playServiceId)

        return launchFragmentInContainer(bundle) {
            TemplateFragment.newInstance(nuguAndroidProvider,
                externalViewRenderer,
                templateLoadingListener,
                templateHandlerFactory,
                templateTypeMedia,
                dialogRequestId,
                templateId,
                parentTemplateId,
                templateString,
                displayType,
                playServiceId)
        }
    }

    @Before
    fun setup() {
        audioPlayerViewType1 = DisplayAudioPlayer(TemplateView.AUDIO_PLAYER_TEMPLATE_1, ApplicationProvider.getApplicationContext())
        audioPlayerViewType2 = DisplayAudioPlayer(TemplateView.AUDIO_PLAYER_TEMPLATE_2, ApplicationProvider.getApplicationContext())
        MockitoAnnotations.openMocks(this)
        whenever(nuguClientProvider.getNuguClient()).thenReturn(nuguAndroidClient)
        whenever(nuguAndroidClient.themeManager).thenReturn(themeManager)

        audioPlayerInfo = AudioPlayer(audioPlayerTitle, audioPlayerContent)
    }

    @Test
    fun test_templateHandler() {
        val playerSpy = spy(audioPlayerViewType1)
        val handlerSpy = spy(DefaultTemplateHandler(nuguClientProvider, templateInfo, fragment))
        playerSpy.templateHandler = handlerSpy

        `when`(nuguAndroidClient.themeManager.theme).thenReturn(ThemeManagerInterface.THEME.DARK)
        playerSpy.onThemeChange(ThemeManagerInterface.THEME.DARK)

        `when`(nuguAndroidClient.themeManager.theme).thenReturn(ThemeManagerInterface.THEME.LIGHT)
        playerSpy.onThemeChange(ThemeManagerInterface.THEME.LIGHT)

        verify(playerSpy, atLeastOnce()).updateThemeIfNeeded()
        verify(handlerSpy).setClientListener(any())
        verify(handlerSpy, atLeast(2)).getNuguClient()
    }

    @Test
    fun test_theme() {
        val playerSpy = spy(audioPlayerViewType1)
        val handlerSpy = spy(DefaultTemplateHandler(nuguClientProvider, templateInfo, fragment))
        playerSpy.templateHandler = handlerSpy

        `when`(nuguAndroidClient.themeManager.theme).thenReturn(ThemeManagerInterface.THEME.DARK)
        playerSpy.onThemeChange(ThemeManagerInterface.THEME.DARK)

        `when`(nuguAndroidClient.themeManager.theme).thenReturn(ThemeManagerInterface.THEME.LIGHT)
        playerSpy.onThemeChange(ThemeManagerInterface.THEME.LIGHT)

        verify(playerSpy, atLeast(2)).updateThemeIfNeeded()
    }

    @Test
    fun test_mediaListener() {
        val spy = spy(audioPlayerViewType1)
        spy.mediaListener.onMediaStateChanged(AudioPlayerAgentInterface.State.PAUSED, 0L, 0f, false)
        spy.mediaListener.onMediaStateChanged(AudioPlayerAgentInterface.State.PLAYING, 0L, 0f, false)

        // check item null
        println("${spy.audioPlayerItem}")
        spy.mediaListener.onMediaDurationRetrieved(1000)
        spy.mediaListener.onMediaProgressChanged(10f, 100)

        // check item is not null && content.durationSec is null
        spy.audioPlayerItem = emptyAudioPlayerInfo
        whenever(emptyAudioPlayerInfo.content.durationSec).thenReturn(null)
        println("${spy.audioPlayerItem} / ${spy.audioPlayerItem!!.content.durationSec}")
        spy.mediaListener.onMediaDurationRetrieved(1000)
        spy.mediaListener.onMediaProgressChanged(20f, 200)

        // check item is not null && content.durationSec is not null
        spy.audioPlayerItem = audioPlayerInfo
        println("${spy.audioPlayerItem} / ${spy.audioPlayerItem?.content?.durationSec}")
        spy.mediaListener.onMediaDurationRetrieved(1000)
        spy.mediaListener.onMediaProgressChanged(30f, 300)

        assertEquals(spy.audioPlayerItem!!.content, audioPlayerContent)
    }

    @Test
    fun test_update_empty() {
        val audioPlayerUpdate = AudioPlayerUpdate(emptyAudioPlayerInfo)
        val spy = spy(audioPlayerViewType1)
        spy.update(gson.toJson(audioPlayerUpdate), "dialogID")
        verify(spy, never()).load(emptyAudioPlayerInfo, true)
    }

    @Test
    fun test_update() {
        val audioPlayerUpdate = AudioPlayerUpdate(audioPlayerInfo)
        val spy = spy(audioPlayerViewType1)
        spy.update(gson.toJson(audioPlayerUpdate), "dialogID")
        verify(spy).load(any(), eq(true))
    }

    @Test
    fun test_click() {
        whenever(nuguAndroidClient.getPlaybackRouter()).thenReturn(mock())
        whenever(nuguAndroidClient.audioPlayerAgent).thenReturn(mock())

        launchFragment().onFragment {
            // check initial state is 'collapsed'
            onView(withId(R.id.view_music_player)).check(matches(not(isCompletelyDisplayed())))

            // expand and check
            onView(withId(R.id.bar_body)).perform(click())
            onView(withId(R.id.view_music_player)).check(matches(isDisplayed()))

            // click buttons
            onView(withId(R.id.btn_collapsed)).perform(click())
            onView(withId(R.id.btn_bar_play)).perform(click())
            onView(withId(R.id.btn_bar_prev)).perform(click())
            onView(withId(R.id.btn_bar_next)).perform(click())
            onView(withId(R.id.btn_bar_close)).perform(click())
        }
    }

    @Test
    fun test_click_onNews() {
        whenever(nuguAndroidClient.getPlaybackRouter()).thenReturn(mock())
        whenever(nuguAndroidClient.audioPlayerAgent).thenReturn(mock())

        launchFragment(template_news).onFragment {
            // expand
            onView(withId(R.id.bar_body)).perform(click())

            // click 'show lyrics button' and check
            onView(withId(R.id.tv_show_lyrics)).perform(scrollTo(), click())
            onView(withId(R.id.cv_lyrics)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test_click_onPOP() {
        whenever(nuguAndroidClient.getPlaybackRouter()).thenReturn(mock())
        whenever(nuguAndroidClient.audioPlayerAgent).thenReturn(mock())

        launchFragment(template_pop).onFragment {
            // expand
            onView(withId(R.id.bar_body)).perform(click())

            // click 'shuffle button' and check
            onView(withId(R.id.iv_shuffle)).perform(click())
            verify(templateHandlerSpy).onPlayerCommand(eq(PlayerCommand.SHUFFLE), any())

            // click 'favorite button' and check
            onView(withId(R.id.iv_favorite)).perform(click())
            verify(templateHandlerSpy).onPlayerCommand(eq(PlayerCommand.FAVORITE), any())

            // click 'repeat button' and check
            onView(withId(R.id.iv_repeat)).perform(click())
            verify(templateHandlerSpy).onPlayerCommand(eq(PlayerCommand.REPEAT), any())

            // click 'small lyrics view' and check
            onView(withId(R.id.cv_small_lyrics)).perform(scrollTo(), click())
            onView(withId(R.id.cv_lyrics)).check(matches(isDisplayed()))

            // click 'lyrics view' and check
            onView(withId(R.id.cv_lyrics)).perform(click())
            onView(withId(R.id.cv_lyrics)).check(matches(not(isCompletelyDisplayed())))
        }
    }

}