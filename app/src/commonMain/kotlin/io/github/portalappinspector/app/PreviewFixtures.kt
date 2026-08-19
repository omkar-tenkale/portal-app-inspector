package io.github.portalappinspector.app

import io.github.portalappinspector.app.features.network.PortalNetworkCall

internal object PreviewFixtures {
    val dummyNetworkCall = PortalNetworkCall(
        id = 1L,
        timestampEpochMillis = 1690000000000L,
        method = "GET",
        url = "https://api.test.com/v1/users",
        endpoint = "/v1/users",
        statusCode = 200,
        durationMillis = 145L,
        error = null,
        responseBody = "{\n  \"status\": \"ok\"\n}",
        responseContentType = "application/json",
        responseBodySizeBytes = 24L,
        responseBodyTruncated = false,
        isMocked = true
    )
}
