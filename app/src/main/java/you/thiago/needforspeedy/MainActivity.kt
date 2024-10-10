package you.thiago.needforspeedy

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var isInitialized = false
    private var scoreCurrent = 0

    private lateinit var gameContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        gameContainer = findViewById(R.id.layout_land_box)

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
        startGeneratingObstacles()
    }

    private fun setupScore() {
        findViewById<TextView>(R.id.txt_score_current).text = "%s km".format((scoreCurrent++).toString())
        Handler(Looper.getMainLooper()).postDelayed(::setupScore, 200)
    }

    private fun startGeneratingObstacles() {
        lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                generateObstacle() // Generate a new obstacle
                delay(Random.nextLong(1000, 2000)) // Wait for 1 to 2 seconds before generating next
            }
        }
    }

    private fun generateObstacle() {
        val obstacle = View(this).apply {
            val playerSize = 80
            val randomHeight = getRandomHeight()
            val floatingObject = Random.nextInt(0, 15) < 2

            setBackgroundColor(getRandomColor()) // You can replace with an image or shape
            layoutParams = FrameLayout.LayoutParams(getRandomHWidth(), randomHeight) // Adjust obstacle size
            x = gameContainer.width.toFloat() // Start from the right edge of the screen

            y = if (floatingObject) {
                val floatingDistance = Random.nextInt(playerSize, playerSize * 3)
                (gameContainer.height - randomHeight - floatingDistance).toFloat() // Floating Object
            } else {
                (gameContainer.height - randomHeight).toFloat() // Fixed on bottom
            }
        }

        gameContainer.addView(obstacle) // Add the obstacle to the game layout
        moveObstacle(obstacle) // Start moving the obstacle
    }

    private fun moveObstacle(obstacle: View) {
        obstacle.animate()
            .translationX(-gameContainer.width.toFloat()) // Move to the left side of the screen
            .setDuration(3000L) // Speed of the obstacle
            .withEndAction {
                gameContainer.removeView(obstacle) // Remove the obstacle when it leaves the screen
            }
            .start()
    }

    private fun getRandomColor(): Int {
        // Generate a random color for each obstacle (just for demo purposes)
        return android.graphics.Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
    }

    private fun getRandomHWidth(): Int {
        return Random.nextInt(50, 100)
    }

    private fun getRandomHeight(): Int {
        return Random.nextInt(100, 300)
    }
}