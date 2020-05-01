package pl.todoit.IndustrialWebViewWithQr.model

sealed class Choice2<FirstT,SecondT> {
    class Choice1Of2<FirstT,SecondT>(public val value:FirstT) : Choice2<FirstT, SecondT>()
    class Choice2Of2<FirstT,SecondT>(public val value:SecondT) : Choice2<FirstT, SecondT>()
}