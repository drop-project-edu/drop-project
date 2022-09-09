/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2022 Pedro Alves
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package edu.uoc.lti.claims

/**
 * This shadows the class ClaimsEnum in the lti-13-core library (1.0.0)
 * to add the EXT claim, already discussed with Xavier (owner of the library)
 *
 * TODO: Remove this class after lti-13-core-1.0.1 is released
 */
enum class ClaimsEnum(val nome: String) {

    KID("kid"), MESSAGE_TYPE("https://purl.imsglobal.org/spec/lti/claim/message_type"), GIVEN_NAME("given_name"), FAMILY_NAME("family_name"), MIDDLE_NAME(
        "middle_name"
    ),
    PICTURE("picture"), EMAIL("email"), NAME("name"), NONCE("nonce"), VERSION("https://purl.imsglobal.org/spec/lti/claim/version"), LOCALE("locale"), RESOURCE_LINK(
        "https://purl.imsglobal.org/spec/lti/claim/resource_link"
    ),
    CONTEXT("https://purl.imsglobal.org/spec/lti/claim/context"), ROLES("https://purl.imsglobal.org/spec/lti/claim/roles"), TOOL_PLATFORM("https://purl.imsglobal.org/spec/lti/claim/tool_platform"), ASSIGNMENT_GRADE_SERVICE(
        "https://purl.imsglobal.org/spec/lti-ags/claim/endpoint"
    ),
    NAMES_ROLE_SERVICE("https://purl.imsglobal.org/spec/lti-nrps/claim/namesroleservice"), CALIPER_SERVICE("https://purl.imsglobal.org/spec/lti-ces/claim/caliper-endpoint-service"), PRESENTATION(
        "https://purl.imsglobal.org/spec/lti/claim/launch_presentation"
    ),
    CUSTOM("https://purl.imsglobal.org/spec/lti/claim/custom"), TARGET_LINK_URI("https://purl.imsglobal.org/spec/lti/claim/target_link_uri"), ROLE_SCOPE_MENTOR(
        "https://purlimsglobal.org/spec/lti/claim/role_scope_mentor"
    ),
    DEPLOYMENT_ID("https://purl.imsglobal.org/spec/lti/claim/deployment_id"), AUTHORIZED_PART("azp"), DEEP_LINKING_SETTINGS("https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings"), DEEP_LINKING_CONTENT_ITEMS(
        "https://purl.imsglobal.org/spec/lti-dl/claim/content_items"
    ),
    DEEP_LINKING_MESSAGE("https://purl.imsglobal.org/spec/lti-dl/claim/msg"), DEEP_LINKING_LOG("https://purl.imsglobal.org/spec/lti-dl/claim/log"), DEEP_LINKING_ERROR_MESSAGE(
        "https://purl.imsglobal.org/spec/lti-dl/claim/errormsg"
    ),
    DEEP_LINKING_ERROR_LOG("https://purl.imsglobal.org/spec/lti-dl/claim/errorlog"), DEEP_LINKING_DATA("https://purl.imsglobal.org/spec/lti-dl/claim/data"), EXT(
        "https://purl.imsglobal.org/spec/lti/claim/ext"
    );

    fun getName(): String {
        return nome
    }
}
