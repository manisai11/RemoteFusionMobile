package com.manisai.remotefusionmobile

data class DiscoveredDevice(
    val name: String,
    val ipAddress: String,
    val port: String
) {
    /**
     * This is crucial. The ArrayAdapter uses this function to get the
     * text to display in the list. We return the name so the list shows
     * "My-PC" instead of the full object details.
     */
    override fun toString(): String {
        return name
    }
}