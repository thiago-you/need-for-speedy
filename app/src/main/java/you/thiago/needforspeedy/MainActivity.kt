package you.thiago.needforspeedy

import android.animation.ValueAnimator
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
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

    private lateinit var gameContainer: ConstraintLayout
    private var mediaPlayer: MediaPlayer? = null

    private var soundPool: SoundPool? = null
    private var soundCarId: Int = 0
    private var soundJumpId: Int = 0

    private var isInitialized = false
    private var isRestarted = false
    private var isInDebugMode = false
    private var isGameRunning = true
    private var scoreCurrent = 0
    private var collisionLeniency = 150
    private var collisionLeniencyBottom = 300

    private var pressStartTime: Long = 0
    private var isJumping = false
    private var initialY = 0f

    private val preferencesData by lazy { getSharedPreferences("needforspeedy", MODE_PRIVATE) }

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

        soundPool?.release()
        soundPool = null
    }

    private fun initialize() {
        setupMaxScore()
        setupActions()
        initSoundEffects()
    }

    private fun setupMaxScore() {
        val savedScore = preferencesData.getString("score", "0")?.toInt() ?: 0
        findViewById<TextView>(R.id.txt_score_max).text = "%s km".format(savedScore)
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
            if (!isGameRunning) {
                return@setOnTouchListener false
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
        findViewById<TextView>(R.id.action_game_state).visibility = View.GONE
        findViewById<TextView>(R.id.action_restart_game).visibility = View.GONE

        isInitialized = true
        isGameRunning = true
        scoreCurrent = 0

        findViewById<TextView>(R.id.txt_score_current).text = "0 km"

        setupScore()
        playCarSoundEffect()

        Handler(Looper.getMainLooper()).postDelayed({
            startPlayer()
            startGeneratingObstacles()
            startBackgroundMusic()
        }, 1000)
    }

    private fun setupScore() {
        if (isGameRunning) {
            findViewById<TextView>(R.id.txt_score_current).text = "%s km".format((scoreCurrent++).toString())
            Handler(Looper.getMainLooper()).postDelayed(::setupScore, 200)
        }
    }

    private fun startPlayer() {
        findViewById<LottieAnimationView>(R.id.lottie_player).apply {
            playAnimation()

            if (isRestarted) {
                return
            }

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

        playJumpSoundEffect()

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
            while (isGameRunning) {
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
        val player = findViewById<LottieAnimationView>(R.id.lottie_player)

        obstacle.animate()
            .translationX(-gameContainer.width.toFloat()) // Move to the left side of the screen
            .setDuration(4000L) // Speed of the obstacle
            .setUpdateListener {
                if (detectCollision(player, obstacle)) {
                    handleCollision()
                }
            }
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
        Handler(Looper.getMainLooper()).postDelayed({
            mediaPlayer = MediaPlayer.create(this, R.raw.topgear_1)
            mediaPlayer?.isLooping = true
            mediaPlayer?.setVolume(0.4f, 0.4f)
            mediaPlayer?.start()
        }, 500)
    }

    private fun startGameOverMusic() {
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, R.raw.gameover)
        mediaPlayer?.start()
    }

    private fun initSoundEffects() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.load(this, R.raw.car_sound_3, 1)?.also {
            soundCarId = it
        }

        soundPool?.load(this, R.raw.jump_3, 2)?.also {
            soundJumpId = it
        }
    }

    private fun playCarSoundEffect() {
        if (soundCarId != 0) {
            // (soundId, leftVolume, rightVolume, priority, loop, rate)
            soundPool?.play(soundCarId, 2f, 2f, 1, 0, 1f)
        }
    }

    private fun playJumpSoundEffect() {
        if (soundJumpId != 0) {
            // (soundId, leftVolume, rightVolume, priority, loop, rate)
            soundPool?.play(soundJumpId, 1f, 1f, 2, 0, 1f)
        }
    }

    private fun handleCollision() {
        if (isGameRunning) {
            findViewById<TextView>(R.id.action_game_state).apply {
                this.visibility = View.VISIBLE
                this.text = "Game Over!"
            }

            startGameOverMusic()
            findViewById<LottieAnimationView>(R.id.lottie_player).cancelAnimation()

            findViewById<TextView>(R.id.action_restart_game).visibility = View.VISIBLE

            isInitialized = false
            isRestarted = true

            saveScore()
        }

        isGameRunning = false
    }

    private fun detectCollision(a: View, b: View): Boolean {
        val ar = Rect()
        val br = Rect()

        a.getHitRect(ar)
        b.getHitRect(br)

        return ar.intersects(
            br.left + collisionLeniency,
            br.top + collisionLeniencyBottom,
            br.right + collisionLeniency,
            br.bottom + collisionLeniencyBottom
        )
    }

    private fun saveScore() {
        val currentScore = getCurrentScore()
        val savedScore = preferencesData.getString("score", "0")?.toInt() ?: 0

        if (currentScore > savedScore) {
            with(preferencesData.edit()) {
                putString("score", currentScore.toString())
                apply()
            }

            findViewById<TextView>(R.id.txt_score_max).text = "%s km".format(currentScore)
        }
    }

    private fun getCurrentScore(): Int {
        val score = findViewById<TextView>(R.id.txt_score_current).text
            .toString()
            .replace(" km", "")
            .trim()

        return score.toInt()
    }
}