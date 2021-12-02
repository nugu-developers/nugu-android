/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skt.nugu.sdk.agent.display

import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy

class RenderDirectiveHandler(
    private val controller: Controller
) : AbstractDirectiveHandler(){
    companion object {
        // supported at v1.2
        private const val TAG = "RenderDirectiveHandler"

        // supported types for v1.0
        private const val NAME_FULLTEXT1 = "FullText1"
        private const val NAME_FULLTEXT2 = "FullText2"
        private const val NAME_IMAGETEXT1 = "ImageText1"
        private const val NAME_IMAGETEXT2 = "ImageText2"
        private const val NAME_IMAGETEXT3 = "ImageText3"
        private const val NAME_IMAGETEXT4 = "ImageText4"
        private const val NAME_TEXTLIST1 = "TextList1"
        private const val NAME_TEXTLIST2 = "TextList2"
        private const val NAME_TEXTLIST3 = "TextList3"
        private const val NAME_TEXTLIST4 = "TextList4"
        private const val NAME_IMAGELIST1 = "ImageList1"
        private const val NAME_IMAGELIST2 = "ImageList2"
        private const val NAME_IMAGELIST3 = "ImageList3"
        private const val NAME_CUSTOM_TEMPLATE = "CustomTemplate"

        // supported types for v1.1
        private const val NAME_WEATHER_1 = "Weather1"
        private const val NAME_WEATHER_2 = "Weather2"
        private const val NAME_WEATHER_3 = "Weather3"
        private const val NAME_WEATHER_4 = "Weather4"
        private const val NAME_WEATHER_5 = "Weather5"
        private const val NAME_FULLIMAGE = "FullImage"

        // supported for v1.2
        private const val NAME_FULLTEXT3 = "FullText3"
        
        private const val NAME_SCORE_1 = "Score1"
        private const val NAME_SCORE_2 = "Score2"

        private const val NAME_SEARCH_LIST_1 = "SearchList1"
        private const val NAME_SEARCH_LIST_2 = "SearchList2"

        private const val NAME_COMMERCE_LIST = "CommerceList"
        private const val NAME_COMMERCE_OPTION = "CommerceOption"
        private const val NAME_COMMERCE_PRICE = "CommercePrice"
        private const val NAME_COMMERCE_INFO = "CommerceInfo"

        private const val NAME_CALL_1 = "Call1"
        private const val NAME_CALL_2 = "Call2"
        private const val NAME_CALL_3 = "Call3"

        private const val NAME_TIMER = "Timer"

        // supported for v1.4
        private const val NAME_DUMMY = "Dummy"

        // supported for v1.6
        private const val NAME_UNIFIED_SEARCH1 = "UnifiedSearch1"

        // supported for v1.8
        private const val NAME_TAB_EXTENSION = "TabExtension"

        // supported for v1.9
        private const val NAME_IMAGETEXT5 = "ImageText5"

        private val FULLTEXT1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_FULLTEXT1
        )
        private val FULLTEXT2 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_FULLTEXT2
        )
        private val FULLTEXT3 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_FULLTEXT3
        )

        private val IMAGETEXT1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_IMAGETEXT1
        )
        private val IMAGETEXT2 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_IMAGETEXT2
        )
        private val IMAGETEXT3 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_IMAGETEXT3
        )
        private val IMAGETEXT4 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_IMAGETEXT4
        )
        private val IMAGETEXT5 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_IMAGETEXT5
        )
        private val TEXTLIST1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_TEXTLIST1
        )
        private val TEXTLIST2 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_TEXTLIST2
        )

        private val TEXTLIST3 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_TEXTLIST3
        )

        private val TEXTLIST4 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_TEXTLIST4
        )

        private val IMAGELIST1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_IMAGELIST1
        )
        private val IMAGELIST2 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_IMAGELIST2
        )
        private val IMAGELIST3 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_IMAGELIST3
        )
        private val CUSTOM_TEMPLATE = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_CUSTOM_TEMPLATE
        )

        private val WEATHER1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_WEATHER_1
        )

        private val WEATHER2 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_WEATHER_2
        )

        private val WEATHER3 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_WEATHER_3
        )

        private val WEATHER4 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_WEATHER_4
        )

        private val WEATHER5 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_WEATHER_5
        )

        private val FULLIMAGE = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_FULLIMAGE
        )

        private val SCORE_1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_SCORE_1
        )

        private val SCORE_2 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_SCORE_2
        )

        private val SEARCH_LIST_1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_SEARCH_LIST_1
        )

        private val SEARCH_LIST_2 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_SEARCH_LIST_2
        )

        private val COMMERCE_LIST = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_COMMERCE_LIST
        )

        private val COMMERCE_OPTION = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_COMMERCE_OPTION
        )

        private val COMMERCE_PRICE = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_COMMERCE_PRICE
        )

        private val COMMERCE_INFO = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_COMMERCE_INFO
        )

        private val CALL_1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_CALL_1
        )

        private val CALL_2 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_CALL_2
        )

        private val CALL_3 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_CALL_3
        )

        private val TIMER = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_TIMER
        )

        private val DUMMY = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_DUMMY
        )

        // v1.6
        private val UNIFIED_SEARCH1 = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_UNIFIED_SEARCH1
        )

        private val TAB_EXTENSION = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_TAB_EXTENSION
        )
    }

    interface Controller {
        fun preRender(info: RenderDirectiveInfo)
        fun render(messageId: String, listener: OnResultListener)
        fun cancelRender(messageId: String)

        interface OnResultListener {
            fun onSuccess()
            fun onFailure(message: String)
        }
    }

    data class RenderDirectiveInfo(
        val info: DirectiveInfo,
        val payload: DisplayAgent.TemplatePayload
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, DisplayAgent.TemplatePayload::class.java)
        if (payload == null) {
            setHandlingFailed(info, "[preHandleDirective] invalid Payload")
            return
        }

//        if (info.directive.getNamespaceAndName() != CUSTOM_TEMPLATE && payload.token.isNullOrBlank()) {
//            setHandlingFailed(info, "[preHandleDirective] invalid payload: empty token")
//            return
//        }

        controller.preRender(RenderDirectiveInfo(info, payload))
    }

    override fun handleDirective(info: DirectiveInfo) {
        controller.render(info.directive.getMessageId(), object: Controller.OnResultListener {
            override fun onSuccess() {
                setHandlingCompleted(info)
            }

            override fun onFailure(message: String) {
                setHandlingFailed(info, message)
            }
        })
    }

    override fun cancelDirective(info: DirectiveInfo) {
        controller.cancelRender(info.directive.getMessageId())
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        val blockingPolicy = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
        this[FULLTEXT1] = blockingPolicy
        this[FULLTEXT2] = blockingPolicy
        this[FULLTEXT3] = blockingPolicy
        this[IMAGETEXT1] = blockingPolicy
        this[IMAGETEXT2] = blockingPolicy
        this[IMAGETEXT3] = blockingPolicy
        this[IMAGETEXT4] = blockingPolicy
        this[IMAGETEXT5] = blockingPolicy
        this[TEXTLIST1] = blockingPolicy
        this[TEXTLIST2] = blockingPolicy
        this[TEXTLIST3] = blockingPolicy
        this[TEXTLIST4] = blockingPolicy
        this[IMAGELIST1] = blockingPolicy
        this[IMAGELIST2] = blockingPolicy
        this[IMAGELIST3] = blockingPolicy
        this[CUSTOM_TEMPLATE] = blockingPolicy

        this[WEATHER1] = blockingPolicy
        this[WEATHER2] = blockingPolicy
        this[WEATHER3] = blockingPolicy
        this[WEATHER4] = blockingPolicy
        this[WEATHER5] = blockingPolicy
        this[FULLIMAGE] = blockingPolicy

        this[SCORE_1] = blockingPolicy
        this[SCORE_2] = blockingPolicy
        this[SEARCH_LIST_1] = blockingPolicy
        this[SEARCH_LIST_2] = blockingPolicy

        this[COMMERCE_LIST] = blockingPolicy
        this[COMMERCE_OPTION] = blockingPolicy
        this[COMMERCE_PRICE] = blockingPolicy
        this[COMMERCE_INFO] = blockingPolicy

        this[CALL_1] = blockingPolicy
        this[CALL_2] = blockingPolicy
        this[CALL_3] = blockingPolicy

        this[TIMER] = blockingPolicy

        this[DUMMY] = blockingPolicy

        this[UNIFIED_SEARCH1] = blockingPolicy

        this[TAB_EXTENSION] = blockingPolicy
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }
}