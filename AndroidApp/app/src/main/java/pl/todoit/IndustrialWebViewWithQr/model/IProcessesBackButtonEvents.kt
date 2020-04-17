package pl.todoit.IndustrialWebViewWithQr.model

interface IProcessesBackButtonEvents {
    suspend fun onBackPressedConsumed() : Boolean
}