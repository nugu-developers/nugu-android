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
package com.skt.nugu.sdk.client.configuration
import com.google.gson.annotations.SerializedName

data class ConfigurationMetadata(
    @SerializedName("issuer")
    val issuer: String,
    @SerializedName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerializedName("token_endpoint")
    val tokenEndpoint: String?,
    @SerializedName("jwks_uri")
    val jwksUri: String?,
    @SerializedName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: Array<String>,
    @SerializedName("response_types_supported")
    val responseTypesSupported: Array<String>,
    @SerializedName("grant_types_supported")
    val grantTypesSupported: Array<String>,
    @SerializedName("introspection_endpoint")
    val introspectionEndpoint: String?,
    @SerializedName("introspection_endpoint_auth_methods_supported")
    val introspectionEndpointAuthMethodsSupported: Array<String>,
    @SerializedName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: Array<String>,
    @SerializedName("revocation_endpoint")
    val revocationEndpoint: String?,
    @SerializedName("revocation_endpoint_auth_methods_supported")
    val revocationEndpointAuthMethodsSupported: Array<String>,
    @SerializedName("device_gateway_registry_uri")
    val deviceGatewayRegistryUri: String?,
    @SerializedName("device_gateway_server_grpc_uri")
    val deviceGatewayServerGrpcUri: String?,
    @SerializedName("device_gateway_server_h2_uri")
    val deviceGatewayServerH2Uri: String?,
    @SerializedName("template_server_uri")
    val templateServerUri: String?,
    @SerializedName("op_policy_uri")
    val policyUri: String?,
    @SerializedName("op_tos_uri")
    val termOfServiceUri: String,
    @SerializedName("service_documentation")
    val serviceDocumentation: String?,
    @SerializedName("service_setting")
    val serviceSetting: String?
)