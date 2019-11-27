/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.core.interfaces.context

// TODO : 의미가 별로 직관적이지 않은듯
enum class StateRefreshPolicy {
    NEVER,  // State의 변경은 변경하는 주체에서 설정하며, Context 필요시 다시 get하지 않고 현재 설정된 것을 쓴다.
    ALWAYS, // 항상 State는 갱신되야하며, 항상 Context에 포함되어야한다.
    SOMETIMES // 항상 State는 갱신되어야하지만, State가 Empty일 경우에 Context에 포함되지 않는다.
}