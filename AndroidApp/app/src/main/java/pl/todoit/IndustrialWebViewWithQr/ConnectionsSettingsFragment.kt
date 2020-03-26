package pl.todoit.IndustrialWebViewWithQr

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText

class ConnectionsSettingsFragment : Fragment() {

    fun setInput(inp : ConnectionInfo) {
        var editor = view?.findViewById<EditText>(R.id.inpUrl)

        if (editor == null) {
            return
        }

        editor.setText(inp.url)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_connections_settings, container, false)

        result.findViewById<Button>(R.id.btnSave)?.setOnClickListener {
            var app = activity?.application
            var editor = view?.findViewById<EditText>(R.id.inpUrl)

            if (app is App && editor != null) {
                app.currentConnection.url = editor.text.toString()
                app.hideConnectionsSettings?.invoke()
            }
        }
        return result
    }
}
