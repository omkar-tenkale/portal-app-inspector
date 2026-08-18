package io.github.portalappinspector.app.features.sharedprefs

internal data class PortalSharedPrefItem(
    val key: String,
    val valueType: String,
    val value: String,
)