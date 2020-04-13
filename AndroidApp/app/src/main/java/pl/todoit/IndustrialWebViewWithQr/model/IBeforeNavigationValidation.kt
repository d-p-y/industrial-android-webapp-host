package pl.todoit.IndustrialWebViewWithQr.model

import androidx.appcompat.app.AppCompatActivity

interface IBeforeNavigationValidation {
    suspend fun maybeGetBeforeNavigationError(act: AppCompatActivity) : String?
}
