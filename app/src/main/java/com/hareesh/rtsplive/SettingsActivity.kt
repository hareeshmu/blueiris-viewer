package com.hareesh.rtsplive

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)

        val urlInput = findViewById<EditText>(R.id.input_url)
        val transportGroup = findViewById<RadioGroup>(R.id.group_transport)
        val reconnectInput = findViewById<EditText>(R.id.input_reconnect)
        val autostart = findViewById<CheckBox>(R.id.check_autostart)
        val save = findViewById<Button>(R.id.btn_save)

        val cfg = Prefs.load(this)
        urlInput.setText(cfg.url)
        transportGroup.check(if (cfg.preferTcp) R.id.radio_tcp else R.id.radio_udp)
        reconnectInput.setText(cfg.reconnectSeconds.toString())
        autostart.isChecked = cfg.autoStart

        save.setOnClickListener {
            try {
                Prefs.save(
                    this,
                    StreamConfig(
                        url = urlInput.text.toString(),
                        preferTcp = transportGroup.checkedRadioButtonId == R.id.radio_tcp,
                        reconnectSeconds = reconnectInput.text.toString().toIntOrNull() ?: 3,
                        autoStart = autostart.isChecked,
                    )
                )
                finish()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
