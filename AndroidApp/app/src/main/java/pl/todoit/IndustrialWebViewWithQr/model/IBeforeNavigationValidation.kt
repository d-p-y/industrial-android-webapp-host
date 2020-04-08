package pl.todoit.IndustrialWebViewWithQr.model

interface IBeforeNavigationValidation {
    suspend fun maybeGetBeforeNavigationError() : String?
}
