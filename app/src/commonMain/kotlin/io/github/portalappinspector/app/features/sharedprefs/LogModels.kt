package io.github.portalappinspector.app.features.sharedprefs

import kotlinx.serialization.Serializable

@Serializable
internal data class PortalSharedPrefItem(
    val key: String,
    val valueType: String,
    val value: String,
)