package com.gmail.chauhanparth86.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.gmail.chauhanparth86.mymemory.models.*
import com.gmail.chauhanparth86.mymemory.utils.DEFAULT_ICONS
import com.gmail.chauhanparth86.mymemory.utils.EXTRA_BOARD_SIZE
import com.gmail.chauhanparth86.mymemory.utils.EXTRA_GAME_NAME
import com.google.android.material.snackbar.Snackbar
import com.google.common.primitives.UnsignedBytes.toInt
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.internal.Sleeper
import com.squareup.picasso.Picasso
import java.util.function.IntToLongFunction

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 119
        private const val HAPTICS_CODE = "haptics"
    }

    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter

    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView
    private lateinit var clRoot: CoordinatorLayout
    private var boardSize: BoardSize = BoardSize.Easy
    private var isitChecked: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPreference:SharedPreference=SharedPreference(this)
        isitChecked = sharedPreference.getValueBoolien(HAPTICS_CODE, false)

        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)
        clRoot = findViewById(R.id.clRoot)

        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))

        setupBoard();
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val checkable: MenuItem = menu.findItem(R.id.mi_haptics)
        checkable.isChecked = isitChecked
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog("Quit your currrent game? You'll loose your progress!", null, View.OnClickListener {
                        setupBoard()
                    })
                } else {
                    setupBoard()
                }
                return true
            }
            R.id.mi_new_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
            R.id.mi_haptics -> {
                switchHaptics(item)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun switchHaptics(item: MenuItem) {
        isitChecked = !isitChecked
        val sharedPreference:SharedPreference=SharedPreference(this)
        sharedPreference.save(HAPTICS_CODE, isitChecked)
        item.isChecked = isitChecked
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        val boardDownloadView =
            LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView, View.OnClickListener {
            // Grab text that we need to get from firebase
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDonwloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(
                    clRoot,
                    "Sorry, we couldn't find any such game, '$customGameName",
                    Snackbar.LENGTH_LONG
                ).show()
                return@addOnSuccessListener
            }
            val numCards: Int = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl: String in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "You're now playing '$customGameName'!", Snackbar.LENGTH_LONG)
                .show()
            gameName = customGameName
            setupBoard()
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun showCreationDialog() {

        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        showAlertDialog("Create your own Memory Board ", boardSizeView, View.OnClickListener {
            // Set a new value for the board size
            val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rb_Easy -> BoardSize.Easy
                R.id.rb_Medium -> BoardSize.Medium
                R.id.rb_Insane -> BoardSize.Insane

                else -> BoardSize.Hard
            }
            // Navigate to new activity
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        when (boardSize) {
            BoardSize.Easy -> radioGroupSize.check(R.id.rb_Easy)
            BoardSize.Medium -> radioGroupSize.check(R.id.rb_Medium)
            BoardSize.Hard -> radioGroupSize.check(R.id.rb_Hard)
            BoardSize.Insane -> radioGroupSize.check(R.id.rb_Insane)
        }

        showAlertDialog("Choose new Level", boardSizeView, View.OnClickListener {
            // Set a new value for the board size
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rb_Easy -> BoardSize.Easy
                R.id.rb_Medium -> BoardSize.Medium
                R.id.rb_Insane -> BoardSize.Insane

                else -> BoardSize.Hard
            }
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    private fun showAlertDialog(
        title: String,
        view: View?,
        positiveClickListener: View.OnClickListener
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Yes") { _, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun setupBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.Easy -> {
                tvNumMoves.text = "Easy: 4 x 2"
                tvNumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.Medium -> {
                tvNumMoves.text = "Medium: 6 x 3"
                tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.Hard -> {
                tvNumMoves.text = "Hard: 6 x 4"
                tvNumPairs.text = "Pairs: 0 / 12"
            }
            BoardSize.Insane -> {
                tvNumMoves.text = "Insane: 8 x 6"
                tvNumPairs.text = "Pairs: 0 / 24"
            }
        }

        val chosenImages: List<Int> = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
        val randomizedImages: List<Int> = (chosenImages + chosenImages).shuffled()
        val memoryCards: List<MemoryCard> = randomizedImages.map { MemoryCard(it) }

        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)

        adapter = MemoryBoardAdapter(
            this,
            boardSize,
            memoryGame.cards,
            object : MemoryBoardAdapter.CardClickListener {
                override fun onCardClicked(position: Int) {
                    updateGameWithFlip(position)
                }

            })
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())

    }

    private fun updateGameWithFlip(position: Int) {
        //Error Checking
        if (memoryGame.haveWonGame()) {
            // Alert user
            Snackbar.make(clRoot, "You Won Already!!! Congratulations", Snackbar.LENGTH_SHORT)
                .show()
            return
        }

        if (memoryGame.isCardFaceUp(position)) {
            // Alert User
            Snackbar.make(clRoot, "Invalid Move", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found A Match! Num Pairs found: ${memoryGame.numPairsFound}")
            if (isitChecked) {
                val v = (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(
                        VibrationEffect.createOneShot(
                            100,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    v.vibrate(100)
                }
            }

            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int

            tvNumPairs.setTextColor(color)

            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, "You Won!!! Congratulations", Snackbar.LENGTH_LONG).show()
                createConfetti()
            }
        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }

    private fun createConfetti() {
        val containerMiddleX = clRoot.getWidth();
        val containerMiddleY = clRoot.getHeight();
        val handler = Handler()
        val v = (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

            var i = (containerMiddleX / 2)
            var j = (containerMiddleY / 2)

            CommonConfetti.rainingConfetti(clRoot,intArrayOf(Color.YELLOW, Color.GREEN, Color.RED, Color.BLUE, Color.BLACK)).stream(1000)

            CommonConfetti.explosion(
                clRoot, (i), (i),
                intArrayOf(Color.YELLOW, Color.GREEN, Color.RED, Color.BLUE, Color.BLACK)
            )
                .stream(1000)

        CommonConfetti.explosion(
            clRoot, (i), (j*2),
            intArrayOf(Color.YELLOW, Color.GREEN, Color.RED, Color.BLUE, Color.BLACK)
        )
            .stream(1000)

        CommonConfetti.explosion(
            clRoot, (i), (j),
            intArrayOf(Color.YELLOW, Color.GREEN, Color.RED, Color.BLUE, Color.BLACK)
        )
            .stream(1000)

        val numbers: LongArray =
            longArrayOf(
                0, 100, 100, 200, 200, 300, 100, 100, 200, 400, 100, 100, 100
            )
        val amp: IntArray =
            intArrayOf(10, 0, 20, 0, 150, 0, 255, 50, 100, 0, 75, 0, 50)

        if (isitChecked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.i(TAG, "Vibration Started")
                v.vibrate(VibrationEffect.createWaveform(numbers, amp, -1))
            } else {
                v.vibrate(500)
            }
        }
    }
}