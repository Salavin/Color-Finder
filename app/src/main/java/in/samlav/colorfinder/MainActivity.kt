package `in`.samlav.colorfinder

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.palette.graphics.Palette
import com.example.colorfinder.R


class MainActivity : AppCompatActivity()
{
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
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = Constants.TITLE
        imageView = findViewById(R.id.imageView)
        fromFilesButton = findViewById(R.id.buttonFromFiles)
        fromWallpaperButton = findViewById(R.id.buttonFromWallpaper)

        lightVibrantText = findViewById(R.id.textViewLightVibrant)
        vibrantText = findViewById(R.id.textViewVibrant)
        darkVibrantText = findViewById(R.id.textViewDarkVibrant)
        lightMutedText = findViewById(R.id.textViewLightMuted)
        mutedText = findViewById(R.id.textViewMuted)
        darkMutedText = findViewById(R.id.textViewDarkMuted)

        texts = setOf(
            lightVibrantText,
            vibrantText,
            darkVibrantText,
            lightMutedText,
            mutedText,
            darkMutedText)
        texts.forEach {
            strings[it] = it.text.toString()
        }

        fromFilesButton.setOnClickListener {
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(gallery, Constants.PICK_IMAGE)
        }

        fromWallpaperButton.setOnClickListener {
            getWallpaperPerms()
        }

        val extras = intent.extras
        if (extras != null)
        {
            val fromShortcut = extras[Constants.FROM_SHORTCUT]
            if (fromShortcut == true)
            {
                getWallpaperPerms()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == Constants.PICK_IMAGE)
        {
            imageUri = data?.data
            imageView.setImageURI(imageUri)
            Thread {
                val bitmap = imageUri?.let {
                    ImageDecoder.createSource(
                        applicationContext.contentResolver,
                        it)
                }?.let { ImageDecoder.decodeBitmap(it).copy(Bitmap.Config.RGBA_F16, true) }
                val palette = bitmap?.let { Palette.from(it).generate() }
                if (palette != null)
                {
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
        grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == Constants.REQUEST_STORAGE)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        val checkbox = menu.findItem(R.id.copy_hashtag)
        val sharedPref = getSharedPreferences(Constants.SHARED_PREFERENCES, Context.MODE_PRIVATE)
        checkbox.isChecked = sharedPref.getBoolean(Constants.COPY_HASHTAG, true)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId)
        {
            R.id.copy_hashtag -> handleOptionClick(item)
            R.id.about -> handleAboutClick()
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Handles the clicking of the checkbox in the menu. Inverts the checkbox and updates the value in shared preferences.
     *
     * @param item The checkbox in question
     * @return true
     */
    private fun handleOptionClick(item: MenuItem): Boolean
    {
        val sharedPref = getSharedPreferences(Constants.SHARED_PREFERENCES, Context.MODE_PRIVATE)
        item.isChecked = !item.isChecked
        with(sharedPref.edit()) {
            putBoolean(Constants.COPY_HASHTAG, item.isChecked)
            apply()
        }

        return true
    }

    /**
     * Handles the clicking of the about item in the menu. Starts the About Activity.
     *
     * @param item The MenuItem that got us here
     * @return true
     */
    private fun handleAboutClick(): Boolean
    {
        val intent = Intent(applicationContext, About::class.java)
        startActivityForResult(intent, Constants.ABOUT_INTENT)

        return true
    }

    /**
     * Updates UI element with new swatch
     *
     * @param pair A pair of TextView and Palette.Swatch values to update
     */
    private fun doUIWork(pair: Pair<TextView, Palette.Swatch>)
    {
        val hex = convertRGBtoHex(
            pair.second.rgb.red,
            pair.second.rgb.green,
            pair.second.rgb.blue)
        pair.first.setBackgroundColor(pair.second.rgb)
        pair.first.setTextColor(pair.second.bodyTextColor)
        pair.first.text = strings[pair.first] + " " + hex
        pair.first.setOnClickListener { textView ->
            var finalHex: String? = hex
            val sharedPref = getSharedPreferences(Constants.SHARED_PREFERENCES, Context.MODE_PRIVATE)
            val copyHashtag = sharedPref.getBoolean(Constants.COPY_HASHTAG, true)
            if (!copyHashtag && (hex?.get(0) == '#'))
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

    /**
     * Grabs wallpaper and then performs the UI work
     */
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

    /**
     * Attempts to get user permission to access the wallpaper, or simply executes doWallpaperWork() if permission is already granted
     */
    private fun getWallpaperPerms()
    {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                Constants.REQUEST_STORAGE)
        }
        else
        {
            doWallpaperWork()
        }
    }

    /**
     * Function to convert decimal to hexadecimal
     * Adapted from https://www.geeksforgeeks.org/convert-the-given-rgb-color-code-to-hex-color-code/
     *
     * @author Code_r
     * @param n The decimal in question
     * @return The converted hex in String format
     */
    private fun decToHexa(n: Int): String
    {
        // char array to store hexadecimal number
        var n = n
        val hexaDeciNum = CharArray(2)

        // counter for hexadecimal number array
        var i = 0
        while (n != 0)
        {

            // temporary variable to store remainder
            var temp = 0

            // storing remainder in temp variable.
            temp = n % 16

            // check if temp < 10
            if (temp < 10)
            {
                hexaDeciNum[i] = (temp + 48).toChar()
                i++
            }
            else
            {
                hexaDeciNum[i] = (temp + 55).toChar()
                i++
            }
            n /= 16
        }
        var hexCode = ""
        when (i)
        {
            2 ->
            {
                hexCode += hexaDeciNum[0]
                hexCode += hexaDeciNum[1]
            }
            1 ->
            {
                hexCode = "0"
                hexCode += hexaDeciNum[0]
            }
            0 -> hexCode = "00"
        }

        // Return the equivalent
        // hexadecimal color code
        return hexCode
    }

    /**
     * Function to convert the RGB code to Hex color code
     * Adapted from https://www.geeksforgeeks.org/convert-the-given-rgb-color-code-to-hex-color-code/
     *
     * @author Code_r
     * @param R Red value
     * @param G Green value
     * @param B Blue value
     * @return The RGB value converted to hex in String format
     */
    private fun convertRGBtoHex(R: Int, G: Int, B: Int): String?
    {
        return if (R in 0..255
            && G >= 0 && G <= 255
            && B >= 0 && B <= 255)
        {
            var hexCode: String? = "#"
            hexCode += decToHexa(R)[1] // don't ask why
            hexCode += decToHexa(R)[0]
            hexCode += decToHexa(G)[1]
            hexCode += decToHexa(G)[0]
            hexCode += decToHexa(B)[1]
            hexCode += decToHexa(B)[0]
            hexCode
        }
        else "-1"
    }
}

object Constants
{
    const val PICK_IMAGE = 100
    const val REQUEST_STORAGE = 250
    const val ABOUT_INTENT = 10
    const val COPY_HASHTAG = "copy_hashtag"
    const val FROM_SHORTCUT = "from_shortcut"
    const val SHARED_PREFERENCES = "pref"
    const val TITLE = "Color Finder"
    const val ABOUT_TITLE = "About Color Finder"
    const val PERSONAL_WEBSITE = "https://samlav.in"
    const val REPOSITORY = "https://github.com/Salavin/Color-Finder"
    const val GOOGLE_PAY = "https://gpay.app.goo.gl/pay-9su9NF43f9N"
}