package you.thiago.needforspeedy

import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var gameContainer: FrameLayout
    private var mediaPlayer: MediaPlayer? = null

    private var isInitialized = false
    private var isInDebugMode = false
    private var scoreCurrent = 0

    private var pressStartTime: Long = 0
    private val maxJumpHeight = 300f
    private val minJumpHeight = 100f
    private val jumpUpDuration = 500L // Time to go up
    private val jumpDownDuration = 500L // Time to come down
    private var isJumping = false
    private var initialY = 0f

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

    override fun onDestroy() {
        super.onDestroy()

        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun initialize() {
        setupActions()
    }

    private fun setupActions() {
        findViewById<View>(R.id.action_debug).setOnClickListener {
            isInDebugMode = true
            Toast.makeText(this, "Debug mode enabled. No collision detection.", Toast.LENGTH_LONG).show()
        }

        findViewById<View>(R.id.main).setOnTouchListener { view, event ->
            if (!isInitialized) {
                startGame()
                return@setOnTouchListener true
            }

            val player = findViewById<LottieAnimationView>(R.id.lottie_player)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.performClick()

                    if (!isJumping) {
                        jumpPlayer(player)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isJumping) {
                        cancelPlayerJump(player)
                    }
                }
            }

            return@setOnTouchListener true
        }
    }

    private fun startGame() {
        findViewById<TextView>(R.id.action_jump_hint).visibility = View.GONE

        isInitialized = true

        setupScore()
        startPlayer()
        startGeneratingObstacles()
        startBackgroundMusic()
    }

    private fun setupScore() {
        findViewById<TextView>(R.id.txt_score_current).text = "%s km".format((scoreCurrent++).toString())
        Handler(Looper.getMainLooper()).postDelayed(::setupScore, 200)
    }

    private fun startPlayer() {
        findViewById<LottieAnimationView>(R.id.lottie_player).apply {
            playAnimation()

            val animator = ValueAnimator.ofFloat(0.5f, 0.0f).apply {
                duration = 300L

                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float

                    layoutParams = (layoutParams as ConstraintLayout.LayoutParams).apply {
                        horizontalBias = value
                    }

                    requestLayout()
                }
            }

            animator.start()
        }
    }

    private fun jumpPlayer(player: LottieAnimationView) {
        initialY = player.translationY
        pressStartTime = SystemClock.elapsedRealtime()

        isJumping = true

        // Animate the player upwards and then downwards
        player.animate()
            .translationYBy(-500f) // Move up by jumpHeight
            .setDuration(1500L / 2)
            .withEndAction {
                if (isJumping) {
                    cancelPlayerJump(player)
                }
            }
            .start()
    }

    private fun cancelPlayerJump(player: LottieAnimationView) {
        player.cancelAnimation()

        val pressDuration = SystemClock.elapsedRealtime() - pressStartTime

        val jumpDownDuration = if (pressDuration <= 500) {
            pressDuration * 2
        } else {
            1000L
        }

        // Animate the player upwards and then downwards
        player.animate()
            .translationY(initialY) // Move back down
            .setDuration(jumpDownDuration)
            .withEndAction {
                isJumping = false
            }
            .start()
    }

    private fun startGeneratingObstacles() {
        lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                generateObstacle() // Generate a new obstacle
                delay(Random.nextLong(1500, 2500)) // Wait for 1 to 2 seconds before generating next
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
            .setDuration(4000L) // Speed of the obstacle
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

    private fun startBackgroundMusic() {
        mediaPlayer = MediaPlayer.create(this, R.raw.topgear_1)
        mediaPlayer?.start()
    }

    private fun startGameOverMusic() {
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, R.raw.gameover)
        mediaPlayer?.start()
    }
}