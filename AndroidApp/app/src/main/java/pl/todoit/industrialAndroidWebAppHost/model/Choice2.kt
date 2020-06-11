package pl.todoit.industrialAndroidWebAppHost.model

sealed class Choice2<FirstT,SecondT> {
    class Choice1Of2<FirstT,SecondT>(val value:FirstT) : Choice2<FirstT, SecondT>()
    class Choice2Of2<FirstT,SecondT>(val value:SecondT) : Choice2<FirstT, SecondT>()
}