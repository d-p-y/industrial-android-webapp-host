package pl.todoit.industrialAndroidWebAppHost.model

sealed class Result<SuccessT,ErrorT> {
    class Ok<SuccessT,ErrorT>(public val value:SuccessT) : Result<SuccessT,ErrorT>()
    class Error<SuccessT,ErrorT>(public val error:ErrorT) : Result<SuccessT,ErrorT>()
}
