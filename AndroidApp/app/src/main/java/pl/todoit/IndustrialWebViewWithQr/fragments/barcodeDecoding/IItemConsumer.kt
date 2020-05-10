package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import pl.todoit.IndustrialWebViewWithQr.model.Result

interface IItemConsumer<InputT,OutputT> {
    fun process(inp:InputT) : Result<OutputT, Exception>
}