package pl.todoit.IndustrialWebViewWithQr.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.ConnectionInfo
import pl.todoit.IndustrialWebViewWithQr.model.IHasTitle
import pl.todoit.IndustrialWebViewWithQr.model.IProcessesBackButtonEvents

class ConnectionsSettingsFragment : Fragment(), IProcessesBackButtonEvents, IHasTitle {
    private fun connInfo() = App.Instance.connSettFragmentParams.get()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_connections_settings, container, false)

        result.findViewById<Button>(R.id.btnSave)?.setOnClickListener {
            var editor = view?.findViewById<EditText>(R.id.inpUrl)

            if (editor != null) {
                App.Instance.launchCoroutine {
                    App.Instance.navigation.send(
                        NavigationRequest.ConnectionSettings_Save(ConnectionInfo(editor.text.toString()))
                    )
                }
            }
        }

        var editor = result.findViewById<EditText>(R.id.inpUrl)
        editor.setText(connInfo()?.url)

        return result
    }

    override suspend fun onBackPressedConsumed() : Boolean {
        App.Instance.launchCoroutine {
            App.Instance.navigation.send(NavigationRequest.ConnectionSettings_Back())
        }
        return true
    }

    override fun getTitle(): String = "Connection settings"
}
