package pl.todoit.IndustrialWebViewWithQr.model

import timber.log.Timber

class ParamContainer<T1> {
    private var _v1 : T1? = null

    fun get() : T1? {
        var res = _v1
        if (res == null) {
            Timber.e("ParamContainer parameter is null")
        }
        return res
    }

    fun set(v1 : T1) {
        _v1 = v1
    }
}

class ParamContainer2<T1,T2> {
    private var _v = Pair<T1?,T2?>(null, null)

    fun get() : Pair<T1?,T2?> {
        var res = _v
        if (res.first == null || res.second == null) {
            Timber.e("ParamContainer parameter(s) is null")
        }
        return res
    }

    fun set(v1 : T1, v2 : T2) {
        _v = Pair(v1, v2)
    }
}
