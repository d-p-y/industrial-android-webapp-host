package pl.todoit.industrialAndroidWebAppHost.model

interface IProcessesBackButtonEvents {
    suspend fun onBackPressedConsumed() : Boolean
}