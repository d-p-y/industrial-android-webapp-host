package pl.todoit.IndustrialWebViewWithQr.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.MainActivity
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.ConnectionInfo
import pl.todoit.IndustrialWebViewWithQr.model.IHasTitle
import pl.todoit.IndustrialWebViewWithQr.model.IProcessesBackButtonEvents
import timber.log.Timber

class ConnectionsSettingsFragment : Fragment(), IProcessesBackButtonEvents, IHasTitle {
    lateinit var req : ConnectionInfo

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val result = inflater.inflate(R.layout.fragment_connections_settings, container, false)

        val inputUrl = result.findViewById<EditText>(R.id.inpUrl)
        val inputName = result.findViewById<EditText>(R.id.inpName)
        val createShortcut = result.findViewById<Switch>(R.id.shortcutNeeded)
        val btnSave = result.findViewById<Button>(R.id.btnSave)
        val act = activity

        if (inputUrl == null) {
            Timber.e("no input editor")
            return null
        }

        if (inputName == null) {
            Timber.e("no input name")
            return null
        }

        if (btnSave == null) {
            Timber.e("no btnSave")
            return null
        }

        if (act == null) {
            Timber.e("no activity")
            return null
        }

        //on API 23 calling ShortcutManagerCompat.getDynamicShortcuts(activity) gets empty list even if there are pinned shortcuts

        inputName.doAfterTextChanged {
            btnSave.isEnabled = inputUrl.length() > 0 && inputName.length() > 0
        }

        inputUrl.doAfterTextChanged {
            btnSave.isEnabled = inputUrl.length() > 0 && inputName.length() > 0
        }

        btnSave.setOnClickListener {
            managePinnedShortcut(
                act,
                createShortcut.isChecked,
                inputName.text.toString(),
                inputUrl.text.toString()
            )

            App.Instance.navigator.postNavigateTo(
                NavigationRequest.ConnectionSettings_Save(ConnectionInfo(inputUrl.text.toString(), "hardcodedName")))
        }

        inputUrl.setText(req.url)

        return result
    }

    private fun managePinnedShortcut(act : Activity, needShortcut: Boolean, name : String, url:String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(act)) {
            Timber.e("ShortcutManagerCompat.isRequestPinShortcutSupported is false")
            Toast.makeText(act, "Launcher doesn't support pinned shortcuts", Toast.LENGTH_SHORT).show()
            return
        }

        //on API 23 calling ShortcutManagerCompat.getDynamicShortcuts(activity) gets empty list even if there are pinned shortcuts
        //on API 23 calling ShortcutManagerCompat.removeDynamicShortcuts(activity, id) doesn't seem to remove pinned shortcuts

        if (needShortcut) {
            Toast.makeText(act, "Creating shortcut", Toast.LENGTH_SHORT).show()

            val x = ShortcutInfoCompat.Builder(act, url).apply {
                setShortLabel(name)
                setIcon(IconCompat.createWithResource(act, R.mipmap.ic_launcher))

                //https://developer.android.com/guide/components/activities/tasks-and-back-stack
                setIntent(
                    Intent(act, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        data = Uri.parse(url)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP + Intent.FLAG_ACTIVITY_NEW_TASK
                    })
            }

            ShortcutManagerCompat.requestPinShortcut(act, x.build(), null)
        }
    }

    override suspend fun onBackPressedConsumed() : Boolean {
        App.Instance.navigator.postNavigateTo(NavigationRequest.ConnectionSettings_Back())
        return true
    }

    override fun getTitle(): String = "Connection settings"
}
