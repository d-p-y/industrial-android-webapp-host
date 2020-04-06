package pl.todoit.IndustrialWebViewWithQr.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.MainActivity
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.ConnectionInfo
import pl.todoit.IndustrialWebViewWithQr.model.IProcessesBackButtonEvents

class ConnectionsSettingsFragment(private val navigation : SendChannel<NavigationRequest>, private val connInfo : ConnectionInfo) : Fragment(),
    IProcessesBackButtonEvents {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_connections_settings, container, false)

        result.findViewById<Button>(R.id.btnSave)?.setOnClickListener {
            var act = activity
            var editor = view?.findViewById<EditText>(R.id.inpUrl)

            if (act is MainActivity && editor != null) {
                act.launchCoroutine(suspend {
                    navigation.send(
                        NavigationRequest.ConnectionSettings_Save(
                            ConnectionInfo(
                                editor.text.toString()
                            )
                        )
                    )
                })
            }
        }

        var editor = result.findViewById<EditText>(R.id.inpUrl)
        editor.setText(connInfo.url)

        return result
    }

    override fun onBackPressed() {
        var act = activity

        if (act is MainActivity) {
            act.launchCoroutine(suspend {
                navigation.send(NavigationRequest.ConnectionSettings_Back())
            })
        }
    }
}
