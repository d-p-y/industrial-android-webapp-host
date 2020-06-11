package pl.todoit.industrialAndroidWebAppHost.fragments.barcodeDecoding

import pl.todoit.industrialAndroidWebAppHost.model.Result

interface IItemConsumer<InputT,OutputT> {
    fun process(inp:InputT) : Result<OutputT, Exception>
}