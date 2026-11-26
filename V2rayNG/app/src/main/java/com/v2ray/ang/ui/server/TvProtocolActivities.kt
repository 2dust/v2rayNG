package com.v2ray.ang.ui.server

private val TvProtocolLayout: ServerEditorLayout = { title, onBack, onSave, onDelete, content ->
    TvServerEditor(title, onBack, onSave, onDelete, content = content)
}

// Each destination inherits its protocol's form and validation, not its phone entry point.
class TvServerVmessActivity : BaseServerVmessActivity(TvProtocolLayout)
class TvServerVlessActivity : BaseServerVlessActivity(TvProtocolLayout)
class TvServerShadowsocksActivity : BaseServerShadowsocksActivity(TvProtocolLayout)
class TvServerSocksActivity : BaseServerSocksActivity(TvProtocolLayout)
class TvServerHttpActivity : BaseServerHttpActivity(TvProtocolLayout)
class TvServerTrojanActivity : BaseServerTrojanActivity(TvProtocolLayout)
class TvServerWireguardActivity : BaseServerWireguardActivity(TvProtocolLayout)
class TvServerHysteria2Activity : BaseServerHysteria2Activity(TvProtocolLayout)
class TvServerMasqueActivity : BaseServerMasqueActivity(TvProtocolLayout)
