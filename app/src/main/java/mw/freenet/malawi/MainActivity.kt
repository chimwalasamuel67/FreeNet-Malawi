package mw.freenet.malawi

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(padding, padding * 2, padding, padding)
            setBackgroundColor(0xFFF4F7FB.toInt())
        }

        val title = TextView(this).apply {
            text = "FreeNet Malawi"
            textSize = 28f
            setTextColor(0xFF1557A5.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Learning and research into affordable, lawful connectivity"
            textSize = 16f
            setTextColor(0xFF303943.toInt())
            setPadding(0, padding / 2, 0, padding)
        }
        root.addView(subtitle)

        val label = TextView(this).apply {
            text = "Choose a network to learn about:"
            textSize = 16f
            setTextColor(0xFF202830.toInt())
        }
        root.addView(label)

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Wi-Fi", "Airtel Malawi", "TNM")
            )
        }
        root.addView(
            spinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val explanation = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF303943.toInt())
            setPadding(0, padding, 0, padding)
        }
        root.addView(explanation)

        fun updateExplanation(selection: String) {
            explanation.text = when (selection) {
                "Airtel Malawi" -> "Airtel is a mobile network operator. Internet access through its mobile network may use your active data bundle or applicable plan."
                "TNM" -> "TNM is a mobile network operator. Internet access through its mobile network may use your active data bundle or applicable plan."
                else -> "Wi-Fi access depends on the network owner and the internet service available at that location."
            } + "\n\nThis prototype is for learning. It does not create free data or bypass network charges."
        }

        spinner.setSelection(0)
        updateExplanation("Wi-Fi")
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                updateExplanation(spinner.selectedItem.toString())
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        val status = TextView(this).apply {
            text = "Status: Learning mode — no VPN connection is active."
            textSize = 14f
            setTextColor(0xFF526171.toInt())
            setPadding(0, padding, 0, 0)
        }
        root.addView(status)

        setContentView(root)
    }
}
