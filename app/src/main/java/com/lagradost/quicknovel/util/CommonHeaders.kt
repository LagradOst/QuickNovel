
object CommonHeaders  {
    val useCloudflareKillerHeader = "useCloudflareKiller"
    val useIgnore500Header = "useIgnore500" to "true"
    val cloudflareKillerWaitSelectorHeader = "X-Cloudflare-Wait-Selector"
    val cloudflareKillerWaitScriptHeader = "X-Cloudflare-Wait-Script"

    fun ajaxHeaders(refer:String = "") =
        if(refer.isNotEmpty())
            mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to refer,
                "Accept" to "application/json, text/javascript, */*; q=0.01"
            )
        else emptyMap()
}