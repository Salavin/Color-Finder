package com.example.colorfinder

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.palette.graphics.Palette
import org.w3c.dom.Text
import java.util.jar.Manifest


class MainActivity : AppCompatActivity() {
    lateinit var imageView: ImageView
    lateinit var fromFilesButton: Button
    lateinit var fromWallpaperButton: Button
    lateinit var lightVibrantText: TextView
    lateinit var vibrantText: TextView
    lateinit var darkVibrantText: TextView
    lateinit var lightMutedText: TextView
    lateinit var mutedText: TextView
    lateinit var darkMutedText: TextView
    private lateinit var texts: Set<TextView>
    private lateinit var swatches: Set<Palette.Swatch>
    private var strings: MutableMap<TextView, String> = mutableMapOf()

    private val pickImage = 100
    private val requestStorage = 250
    private var imageUri: Uri?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = "Color Finder"
        imageView = findViewById(R.id.imageView)
        fromFilesButton = findViewById(R.id.buttonFromFiles)
        fromWallpaperButton = findViewById(R.id.buttonFromWallpaper)

        lightVibrantText = findViewById(R.id.textViewLightVibrant)
        vibrantText = findViewById(R.id.textViewVibrant)
        darkVibrantText = findViewById(R.id.textViewDarkVibrant)
        lightMutedText = findViewById(R.id.textViewLightMuted)
        mutedText = findViewById(R.id.textViewMuted)
        darkMutedText = findViewById(R.id.textViewDarkMuted)

        texts = setOf(lightVibrantText, vibrantText, darkVibrantText, lightMutedText, mutedText, darkMutedText)
        texts.forEach {
            strings[it] = it.text.toString()
        }

        fromFilesButton.setOnClickListener {
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(gallery, pickImage)
        }

        fromWallpaperButton.setOnClickListener {
            getWallpaperPerms()
        }

        val extras = intent.extras
        if (extras != null)
        {
            val fromShortcut = extras["from_shortcut"]
            if (fromShortcut == true)
            {
                getWallpaperPerms()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == pickImage) {
            imageUri = data?.data
            imageView.setImageURI(imageUri)
            Thread {
                val bitmap = imageUri?.let {
                    ImageDecoder.createSource(
                        applicationContext.contentResolver,
                        it
                    )
                }?.let { ImageDecoder.decodeBitmap(it).copy(Bitmap.Config.RGBA_F16, true) }
                val palette = bitmap?.let { Palette.from(it).generate() }
                if (palette != null) {
                    swatches = palette.swatches.toSet()
                }
                texts.zip(swatches).forEach {
                    it.first.post {
                        doUIWork(it)
                    }
                }
            }.start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == requestStorage)
        {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                doWallpaperWork()
            }
            else
            {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("In order to access the wallpaper, the app needs permission to access the device media. Please go to settings and grant access to media.")
                    .setNeutralButton("OK", null)
                    .show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        val checkbox = menu.findItem(R.id.copy_hashtag)
        val sharedPref = getSharedPreferences("copy_hashtag", Context.MODE_PRIVATE)
        val copyHashtag = sharedPref.getInt("copy_hashtag", 1)
        checkbox.isChecked = copyHashtag == 1
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.copy_hashtag -> handleOptionClick(item)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleOptionClick(item: MenuItem): Boolean
    {
        val sharedPref = getSharedPreferences("copy_hashtag", Context.MODE_PRIVATE)
        val newVal: Int
        if (item.isChecked)
        {
            item.isChecked = false
            newVal = 0
        }
        else
        {
            item.isChecked = true
            newVal = 1
        }
        with (sharedPref.edit()) {
            putInt("copy_hashtag", newVal)
            apply()
        }

        return true
    }

    private fun doUIWork(pair: Pair<TextView, Palette.Swatch>)
    {
        var hex = convertRGBtoHex(
            pair.second.rgb.red,
            pair.second.rgb.green,
            pair.second.rgb.blue
        )
        pair.first.setBackgroundColor(pair.second.rgb)
        pair.first.setTextColor(pair.second.bodyTextColor)
        pair.first.text = strings[pair.first] + " " + hex
        pair.first.setOnClickListener { textView ->
            var finalHex: String? = hex
            val sharedPref = getSharedPreferences("copy_hashtag", Context.MODE_PRIVATE)
            val copyHashtag = sharedPref.getInt("copy_hashtag", 1)
            if ((copyHashtag == 0) && (hex?.get(0) == '#'))
            {
                finalHex = hex.substring(1)
            }
            val clipboard =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData =
                ClipData.newPlainText("Hex sequence containing color data", finalHex)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                applicationContext,
                "Copied \"$finalHex\" to clipboard.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun doWallpaperWork()
    {
        val wallpaperManager = WallpaperManager.getInstance(applicationContext)
        val wallpaperDrawable = wallpaperManager.drawable
        imageView.setImageDrawable(wallpaperDrawable)
        val bitmap = wallpaperDrawable.toBitmap()
        val palette = Palette.from(bitmap).generate()
        swatches = palette.swatches.toSet()
        texts.zip(swatches).forEach {
            doUIWork(it)
        }
    }

    private fun getWallpaperPerms()
    {
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), requestStorage)
        }
        else
        {
            doWallpaperWork()
        }
    }

    // function to convert decimal to hexadecimal
    private fun decToHexa(n: Int): String {
        // char array to store hexadecimal number
        var n = n
        val hexaDeciNum = CharArray(2)

        // counter for hexadecimal number array
        var i = 0
        while (n != 0) {

            // temporary variable to store remainder
            var temp = 0

            // storing remainder in temp variable.
            temp = n % 16

            // check if temp < 10
            if (temp < 10) {
                hexaDeciNum[i] = (temp + 48).toChar()
                i++
            } else {
                hexaDeciNum[i] = (temp + 55).toChar()
                i++
            }
            n /= 16
        }
        var hexCode = ""
        if (i == 2) {
            hexCode += hexaDeciNum[0]
            hexCode += hexaDeciNum[1]
        } else if (i == 1) {
            hexCode = "0"
            hexCode += hexaDeciNum[0]
        } else if (i == 0) hexCode = "00"

        // Return the equivalent
        // hexadecimal color code
        return hexCode
    }

    // Function to convert the
    // RGB code to Hex color code
    private fun convertRGBtoHex(R: Int, G: Int, B: Int): String? {
        return if (R in 0..255
            && G >= 0 && G <= 255
            && B >= 0 && B <= 255
        ) {
            var hexCode: String? = "#"
            hexCode += decToHexa(R)
            hexCode += decToHexa(G)
            hexCode += decToHexa(B)
            hexCode
        } else "-1"
    }
}