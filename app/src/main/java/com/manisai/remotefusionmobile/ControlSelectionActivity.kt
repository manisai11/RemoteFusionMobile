package com.manisai.remotefusionmobile


import android.content.Intent
import android.os.Bundle
import android.view.View
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ControlSelectionActivity : AppCompatActivity() {

    private lateinit var controlModeSpinner: Spinner
    private lateinit var proceedButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control_selection)

        controlModeSpinner = findViewById(R.id.controlModeSpinner)
        proceedButton = findViewById(R.id.proceedButton)

        val controlModes = listOf("Mobile to PC", "PC to Mobile")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, controlModes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        controlModeSpinner.adapter = adapter

        proceedButton.setOnClickListener {
            val selectedMode = controlModeSpinner.selectedItem.toString()

            when (selectedMode) {
                "Mobile to PC" -> {
                    val intent = Intent(this, DeviceDiscoveryActivity::class.java)
                    startActivity(intent)
                }
                "PC to Mobile" -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)

                }
            }
        }
    }
}