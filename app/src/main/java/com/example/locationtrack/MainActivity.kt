package com.example.locationtrack

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Check GPS tracking
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            finish()
        }
        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (permission == PackageManager.PERMISSION_GRANTED) {
            startTrackerService() //If permission granted
        } else { //request access
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST)
        }

        //setContentView(R.layout.activity_main)
    }
    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        //If the permission has been granted...//
        if (requestCode == PERMISSIONS_REQUEST && grantResults.size == 1 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {

        //...then start the GPS tracking service//
            startTrackerService()
        } else {

        //If the user denies the permission request, then display a toast with more information//
            Toast.makeText(
                this,
                "Please enable location services to allow GPS tracking",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //Start the tracker service
    private fun startTrackerService(){
        startService(Intent(this, TrackingService::class.java))

        //Notify the user that tracking has been enabled//
        Toast.makeText(this, "GPS tracking enabled", Toast.LENGTH_SHORT).show()

        //Close MainActivity//
        finish()
    }

}