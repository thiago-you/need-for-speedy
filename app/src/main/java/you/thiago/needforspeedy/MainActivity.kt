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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    private val minimumVerticalLeniency = 60
    private val minimumHorizontalLeniency = 60

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
            if (!isInDebugMode) {
                isInDebugMode = true
                Toast.makeText(this, "Debug mode enabled. No collision detection.", Toast.LENGTH_LONG).show()
            } else {
                isInDebugMode = false
                Toast.makeText(this, "Debug mode disabled.", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<View>(R.id.main).setOnTouchListener { view, event ->
            if (!isInitialized) {
                startGame()
                return@setOnTouchListener true
            }
            if (!isGameRunning) {
                return@setOnTouchListener false
            }

            val player = findViewById<ImageView>(R.id.img_player)

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

        if (isRestarted) {
            startPlayer()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isRestarted) {
                startPlayer()
            }

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
            progress = 0f
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

                doOnEnd {
                    this@MainActivity.findViewById<ImageView>(R.id.img_player).visibility = View.VISIBLE
                }
            }

            animator.start()
        }
    }

    private fun jumpPlayer(player: ImageView) {
        findViewById<LottieAnimationView>(R.id.lottie_player).visibility = View.INVISIBLE

        initialY = player.translationY
        pressStartTime = SystemClock.elapsedRealtime()

        isJumping = true

        playJumpSoundEffect()

        player.animate()
            .translationYBy(-500f)
            .setDuration(1500L / 2)
            .withEndAction {
                if (isJumping) {
                    cancelPlayerJump(player)
                }
            }
            .start()
    }

    private fun cancelPlayerJump(player: ImageView) {
        val pressDuration = SystemClock.elapsedRealtime() - pressStartTime

        val jumpDownDuration = if (pressDuration <= 500) {
            pressDuration * 2
        } else {
            1000L
        }

        player.animate()
            .translationY(initialY)
            .setDuration(jumpDownDuration)
            .withEndAction {
                isJumping = false
                findViewById<LottieAnimationView>(R.id.lottie_player).visibility = View.VISIBLE
            }
            .start()
    }

    private fun startGeneratingObstacles() {
        lifecycleScope.launch(Dispatchers.Main) {
            while (isGameRunning) {
                generateObstacle()
                delay(Random.nextLong(1800, 2400))
            }
        }
    }

    private fun generateObstacle() {
        val playerSize = 180
        val randomHeight = getRandomHeight()
        val randomWidth = getRandomWidth()
        val floatingObject = Random.nextInt(0, 15) < 3
        val floatingDistance = Random.nextInt(playerSize, playerSize * 3)

        val obstacle = View(this).apply {
            setBackgroundColor(getRandomColor())

            layoutParams = FrameLayout.LayoutParams(randomWidth, randomHeight)
            x = gameContainer.width.toFloat()

            y = if (floatingObject) {
                (gameContainer.height - randomHeight - floatingDistance).toFloat()
            } else {
                (gameContainer.height - randomHeight).toFloat()
            }
        }

        val hitbox = View(this).apply {
            setBackgroundColor(getBlackColor())

            layoutParams = FrameLayout.LayoutParams(randomWidth / 2, randomHeight - 25)
            x = gameContainer.width.toFloat() + 50

            y = if (floatingObject) {
                (gameContainer.height - randomHeight - 25 - floatingDistance).toFloat()
            } else {
                (gameContainer.height - (randomHeight - 25)).toFloat()
            }
        }

        gameContainer.addView(hitbox)
        gameContainer.addView(obstacle)

        moveObstacle(obstacle, hitbox)
    }

    private fun moveObstacle(obstacle: View, hitbox: View) {
        val player = findViewById<ImageView>(R.id.img_player)

        obstacle.animate()
            .translationX(-gameContainer.width.toFloat())
            .setDuration(4000L)
            .withEndAction {
                gameContainer.removeView(obstacle)
            }
            .start()

        hitbox.animate()
            .translationX(-gameContainer.width.toFloat())
            .setDuration(4000L)
            .setUpdateListener {
                if (detectCollision(player, hitbox)) {
                    detectCollision(player, hitbox)
                    handleCollision()
                }
            }
            .withEndAction {
                gameContainer.removeView(hitbox)
            }
            .start()
    }

    private fun getRandomColor(): Int {
        return android.graphics.Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
    }

    private fun getBlackColor(): Int {
        return android.graphics.Color.rgb(0, 0, 0)
    }

    private fun getRandomWidth(): Int {
        return Random.nextInt(100, 150)
    }

    private fun getRandomHeight(): Int {
        return Random.nextInt(100, 300)
    }

    private fun startBackgroundMusic() {
        Handler(Looper.getMainLooper()).postDelayed({
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer.create(this, R.raw.sound_topgear_1)
            mediaPlayer?.isLooping = true
            mediaPlayer?.setVolume(0.4f, 0.4f)
            mediaPlayer?.start()
        }, 500)
    }

    private fun startGameOverMusic() {
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, R.raw.sound_gameover)
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

        soundPool?.load(this, R.raw.sound_car, 1)?.also {
            soundCarId = it
        }

        soundPool?.load(this, R.raw.sound_jump_1, 2)?.also {
            soundJumpId = it
        }
    }

    private fun playCarSoundEffect() {
        if (soundCarId != 0) {
            soundPool?.play(soundCarId, 2f, 2f, 1, 0, 1f)
        }
    }

    private fun playJumpSoundEffect() {
        if (soundJumpId != 0) {
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
            saveScore()

            findViewById<LottieAnimationView>(R.id.lottie_player).cancelAnimation()
            findViewById<LottieAnimationView>(R.id.lottie_player).progress = 0f

            findViewById<TextView>(R.id.action_restart_game).visibility = View.VISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                isInitialized = false
                isRestarted = true
            }, 1500)
        }

        isGameRunning = false
    }

    private fun detectCollision(a: View, b: View): Boolean {
        if (isInDebugMode) {
            return false
        }

        val ar = Rect()
        val br = Rect()

        a.getHitRect(ar)
        b.getHitRect(br)

        if (!Rect.intersects(ar, br)) {
            return false
        }

        val verticalOverlap = abs(ar.bottom - br.top) < minimumVerticalLeniency ||
                              abs(br.bottom - ar.top) < minimumVerticalLeniency

        val horizontalOverlap = abs(ar.right - br.left) < minimumHorizontalLeniency ||
                                abs(br.right - ar.left) < minimumHorizontalLeniency

        return verticalOverlap || horizontalOverlap
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