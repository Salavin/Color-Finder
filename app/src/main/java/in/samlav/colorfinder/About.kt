package `in`.samlav.colorfinder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.colorfinder.R

class About : AppCompatActivity()
{
    lateinit var imageView: ImageView
    lateinit var websiteButton: Button
    lateinit var repositoryButton: Button
    lateinit var googlePayButton: Button

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        title = Constants.ABOUT_TITLE
        this.actionBar?.setDisplayHomeAsUpEnabled(true)

        imageView = findViewById(R.id.appImage)
        websiteButton = findViewById(R.id.websiteButton)
        repositoryButton = findViewById(R.id.repositoryButton)
        googlePayButton = findViewById(R.id.googlePayButton)

        imageView.setOnClickListener {
            Toast.makeText(applicationContext, "\uD83D\uDC23", Toast.LENGTH_SHORT).show()
        }

        websiteButton.setOnClickListener {
            goToUrl(Constants.PERSONAL_WEBSITE)
        }

        repositoryButton.setOnClickListener {
            goToUrl(Constants.REPOSITORY)
        }

        googlePayButton.setOnClickListener {
            goToUrl(Constants.GOOGLE_PAY)
        }
    }

    private fun goToUrl (url: String)
    {
        val uriUrl = Uri.parse(url)
        val launchBrowser = Intent(Intent.ACTION_VIEW, uriUrl)
        startActivity(launchBrowser)
    }
}