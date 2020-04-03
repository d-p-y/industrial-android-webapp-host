package pl.todoit.IndustrialWebViewWithQr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.Channel

class ConnectionsSettingsFragment(private val navigation : Channel<NavigationRequest>, private val connInfo : ConnectionInfo) : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_connections_settings, container, false)

        result.findViewById<Button>(R.id.btnSave)?.setOnClickListener {
            var act = activity
            var editor = view?.findViewById<EditText>(R.id.inpUrl)

            if (act is MainActivity && editor != null) {
                act.launchCoroutine(suspend {
                    navigation.send(NavigationRequest.ConnectionSettings_Save(ConnectionInfo(editor.text.toString())))
                })
            }
        }

        var editor = result.findViewById<EditText>(R.id.inpUrl)
        editor.setText(connInfo.url)

        return result
    }
}
