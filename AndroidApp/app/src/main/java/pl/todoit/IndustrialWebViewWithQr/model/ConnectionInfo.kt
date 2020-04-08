package pl.todoit.IndustrialWebViewWithQr.model

data class ConnectionInfo(var url : String, var forceReloadFromNet : Boolean = true /*easier for development until I add persistence*/)
