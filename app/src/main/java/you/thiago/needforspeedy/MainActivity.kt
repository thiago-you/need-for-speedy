package you.thiago.needforspeedy

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var isInitialized = false
    private var scoreCurrent = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initialize()
    }

    private fun initialize() {
        setupActions()
    }

    private fun setupActions() {
        findViewById<View>(R.id.main).setOnClickListener {
            if (!isInitialized) {
                startGame()
            }
        }
    }

    private fun startGame() {
        findViewById<TextView>(R.id.action_jump_hint).visibility = View.GONE
        isInitialized = true
        setupScore()
    }

    private fun setupScore() {
        findViewById<TextView>(R.id.txt_score_current).text = "%s km".format((scoreCurrent++).toString())
        Handler(Looper.getMainLooper()).postDelayed(::setupScore, 200)
    }
}