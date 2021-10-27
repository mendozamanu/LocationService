package com.example.locationtrack

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import io.realm.Realm
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import org.bson.types.ObjectId


open class Location(
    @PrimaryKey var _id: ObjectId? = ObjectId(),
    var _partition: String = "",
    var accuracy: Float = 0.0F,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var speed: Float = 0.0F,
    var time: Long = 0

): RealmObject()

var user: User? = null
var syncedRealm: Realm? = null

class TrackingService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        buildNotification()
        loginToMongo()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID.toString(), name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    //Create the persistent notification//
    @SuppressLint("UnspecifiedImmutableFlag")
    private fun buildNotification() {

        val stop = "stop"
        registerReceiver(stopReceiver, IntentFilter(stop))
        val broadcastIntent = PendingIntent.getBroadcast(
            this, 0, Intent(stop), PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create the persistent notification//
        val builder = Notification.Builder(this, CHANNEL_ID.toString())
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.tracking_enabled_notif))
            //Make this notification ongoing so it can’t be dismissed by the user//
            .setOngoing(true)
            .setContentIntent(broadcastIntent)
            .setSmallIcon(R.drawable.ic_tracking_enabled)
        startForeground(1, builder.build())
    }

    //Stop the service on notification press
    private var stopReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            //Unregister the BroadcastReceiver when the notification is tapped//

            syncedRealm?.close()
            unregisterReceiver(this)

            //Stop the Service//
            stopSelf()
            //Log.d("Stopped", "Service stopped")
            // Optional: Android should manage itself -> no need to use exitProcess(0)

        }
    }

    private fun loginToMongo(){

        Realm.init(applicationContext)
        val appID = "location-track-timdi"
        val app = App(AppConfiguration.Builder(appID)
            .build())

        val emailPasswordCredentials: Credentials = Credentials.emailPassword(
            getString(R.string.test_email), getString(R.string.test_password)
        )

        app.loginAsync(emailPasswordCredentials){
            if (it.isSuccess){
                Log.i("LoginOK", "Successfully authenticated with test user")
                user = app.currentUser()
                val partitionValue = "Location"
                val config = SyncConfiguration.Builder(user!!, partitionValue)
                    .allowQueriesOnUiThread(true)
                    .allowWritesOnUiThread(true)
                    .build()
                //var realm: Realm
                Realm.getInstanceAsync(config, object: Realm.Callback() {
                    override fun onSuccess(_realm: Realm) {
                        // the realm should live exactly as long as the activity, so assign
                        // the realm to a member variable
                        syncedRealm = _realm

                        requestLocationUpdates2(syncedRealm!!)
                    }

                })

            }
            else{
                Toast.makeText(
                    this,
                    "MongoDB realm authentication error. Try again or contact admin",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }


    //Initiate the request to track the device's location//
    private fun requestLocationUpdates2(realm: Realm) {
        val request = LocationRequest.create()

        //https://docs.mongodb.com/realm/sdk/android/examples/mongodb-remote-access/

        // API Ref for Location:
        // https://developer.android.com/reference/android/location/Location?hl=es-419

        //Specify how often your app should request the device’s location//
        request.interval = 15000 //ms
        request.fastestInterval = 9000 //ms

        //Get the most accurate location data available//
        request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        val client = LocationServices.getFusedLocationProviderClient(this)
        //val path = getString(R.string.firebase_path)
        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION)

        //If the app currently has access to the location permission...//
        if (permission == PackageManager.PERMISSION_GRANTED) {

            //...then request location updates//
            client.requestLocationUpdates(request, object : LocationCallback() {

                override fun onLocationResult(locationResult: LocationResult) {


                    val location = locationResult.lastLocation
                    //Save the location data to the database//

                    Log.d("Location", "Location: $location")

                    realm.beginTransaction()

                    //Creating location data to sync
                    val data = Location(ObjectId(), "Location", location.accuracy,
                        location.latitude, location.longitude, location.speed, location.time)

                    realm.insert(data)
                    realm.commitTransaction()

                    val locquery = realm.where(Location::class.java)
                    val results = locquery.sort("time", Sort.DESCENDING).findAll()
                    Log.d ("QUERY", results.toString())

                }
            }, null)

        }
    }


    companion object {
        private const val CHANNEL_ID = 1945
        //val uid = kotlin.math.abs(Random.nextInt()).toString()
    }
}