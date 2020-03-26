package pl.todoit.IndustrialWebViewWithQr

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText

class ConnectionsSettingsFragment : Fragment() {
    var onPostCreate : ((x:View)->Unit)? = null
    var onQuiting : ((Unit)->Unit)? = null

    fun setNavigation(inp : ConnectionInfo, onQuiting:((Unit)->Unit)) {
        onPostCreate = {
            var editor = it.findViewById<EditText>(R.id.inpUrl)

            if (editor != null) {
                editor.setText(inp.url)
            }
        }
        this.onQuiting = onQuiting
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_connections_settings, container, false)

        result.findViewById<Button>(R.id.btnSave)?.setOnClickListener {
            var app = activity?.application
            var editor = view?.findViewById<EditText>(R.id.inpUrl)

            if (app is App && editor != null) {
                app.currentConnection.url = editor.text.toString()
                onQuiting?.invoke(Unit)
            }
        }
        onPostCreate?.invoke(result)
        return result
    }
}
