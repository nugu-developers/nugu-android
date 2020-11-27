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
import com.skt.nugu.sdk.agent.DefaultDisplayAgent
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

        private val FULLTEXT1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_FULLTEXT1
        )
        private val FULLTEXT2 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_FULLTEXT2
        )
        private val FULLTEXT3 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_FULLTEXT3
        )

        private val IMAGETEXT1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_IMAGETEXT1
        )
        private val IMAGETEXT2 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_IMAGETEXT2
        )
        private val IMAGETEXT3 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_IMAGETEXT3
        )
        private val IMAGETEXT4 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_IMAGETEXT4
        )
        private val TEXTLIST1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_TEXTLIST1
        )
        private val TEXTLIST2 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_TEXTLIST2
        )

        private val TEXTLIST3 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_TEXTLIST3
        )

        private val TEXTLIST4 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_TEXTLIST4
        )

        private val IMAGELIST1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_IMAGELIST1
        )
        private val IMAGELIST2 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_IMAGELIST2
        )
        private val IMAGELIST3 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_IMAGELIST3
        )
        private val CUSTOM_TEMPLATE = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_CUSTOM_TEMPLATE
        )

        private val WEATHER1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_WEATHER_1
        )

        private val WEATHER2 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_WEATHER_2
        )

        private val WEATHER3 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_WEATHER_3
        )

        private val WEATHER4 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_WEATHER_4
        )

        private val WEATHER5 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_WEATHER_5
        )

        private val FULLIMAGE = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_FULLIMAGE
        )

        private val SCORE_1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_SCORE_1
        )

        private val SCORE_2 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_SCORE_2
        )

        private val SEARCH_LIST_1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_SEARCH_LIST_1
        )

        private val SEARCH_LIST_2 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_SEARCH_LIST_2
        )

        private val COMMERCE_LIST = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_COMMERCE_LIST
        )

        private val COMMERCE_OPTION = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_COMMERCE_OPTION
        )

        private val COMMERCE_PRICE = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_COMMERCE_PRICE
        )

        private val COMMERCE_INFO = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_COMMERCE_INFO
        )

        private val CALL_1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_CALL_1
        )

        private val CALL_2 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_CALL_2
        )

        private val CALL_3 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_CALL_3
        )

        private val TIMER = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_TIMER
        )

        private val DUMMY = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_DUMMY
        )

        // v1.6
        private val UNIFIED_SEARCH1 = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_UNIFIED_SEARCH1
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
        val payload: DefaultDisplayAgent.TemplatePayload
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, DefaultDisplayAgent.TemplatePayload::class.java)
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

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val blockingPolicy = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[FULLTEXT1] = blockingPolicy
        configuration[FULLTEXT2] = blockingPolicy
        configuration[FULLTEXT3] = blockingPolicy
        configuration[IMAGETEXT1] = blockingPolicy
        configuration[IMAGETEXT2] = blockingPolicy
        configuration[IMAGETEXT3] = blockingPolicy
        configuration[IMAGETEXT4] = blockingPolicy
        configuration[TEXTLIST1] = blockingPolicy
        configuration[TEXTLIST2] = blockingPolicy
        configuration[TEXTLIST3] = blockingPolicy
        configuration[TEXTLIST4] = blockingPolicy
        configuration[IMAGELIST1] = blockingPolicy
        configuration[IMAGELIST2] = blockingPolicy
        configuration[IMAGELIST3] = blockingPolicy
        configuration[CUSTOM_TEMPLATE] = blockingPolicy

        configuration[WEATHER1] = blockingPolicy
        configuration[WEATHER2] = blockingPolicy
        configuration[WEATHER3] = blockingPolicy
        configuration[WEATHER4] = blockingPolicy
        configuration[WEATHER5] = blockingPolicy
        configuration[FULLIMAGE] = blockingPolicy

        configuration[SCORE_1] = blockingPolicy
        configuration[SCORE_2] = blockingPolicy
        configuration[SEARCH_LIST_1] = blockingPolicy
        configuration[SEARCH_LIST_2] = blockingPolicy

        configuration[COMMERCE_LIST] = blockingPolicy
        configuration[COMMERCE_OPTION] = blockingPolicy
        configuration[COMMERCE_PRICE] = blockingPolicy
        configuration[COMMERCE_INFO] = blockingPolicy

        configuration[CALL_1] = blockingPolicy
        configuration[CALL_2] = blockingPolicy
        configuration[CALL_3] = blockingPolicy

        configuration[TIMER] = blockingPolicy

        configuration[DUMMY] = blockingPolicy

        configuration[UNIFIED_SEARCH1] = blockingPolicy

        return configuration
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }
}