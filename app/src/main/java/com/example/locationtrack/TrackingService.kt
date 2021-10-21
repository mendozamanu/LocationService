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
import com.google.firebase.firestore.FirebaseFirestore
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.kotlin.createObject
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import org.bson.types.ObjectId





class FirebaseUtils {
    val fireStoreDatabase = FirebaseFirestore.getInstance()

}

open class Location(
    @PrimaryKey var _id: ObjectId? = null,
    var accuracy: Float = 0.0F,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var speed: Float = 0.0F,
    var time: Long = 0

): RealmObject()

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

    private var stopReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            //Unregister the BroadcastReceiver when the notification is tapped//
            unregisterReceiver(this)

            //Revisar el IntentReceiver de Realm

            //Stop the Service//
            stopSelf()
            //Log.d("Stopped", "Service stopped")
            // Optional: Android should manage itself -> no need to use exitProcess(0)

        }
    }

    private fun loginToMongo(){

        Realm.init(this)
        //mongodb+srv://testuser:PanCakes@realmcluster.8nqr0.mongodb.net/locationUpdates?retryWrites=true&w=majority
        val appID = "location-track-timdi"
        val app = App(AppConfiguration.Builder(appID)
            .build())

        val emailPasswordCredentials: Credentials = Credentials.emailPassword(
            getString(R.string.test_email), getString(R.string.test_password)
        )
        var user: User?
        app.loginAsync(emailPasswordCredentials){
            if (it.isSuccess){
                Log.i("LoginOK", "Successfully authenticated with test user")
                user = app.currentUser()
                val partitionValue = "Location"
                val config = SyncConfiguration.Builder(user!!, partitionValue)
                    //RealmConfiguration.Builder.allowWritesOnUiThread(true)
                    .build()
                var realm: Realm
                Realm.getInstanceAsync(config, object: Realm.Callback() {
                    override fun onSuccess(_realm: Realm) {
                        // the realm should live exactly as long as the activity, so assign the realm to a member variable
                        realm = _realm
                    }
                })
                requestLocationUpdates2()
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
/*
    private fun loginToFirebase() {

        //Add here AppCheck to allow only the app to access to the firebase project
        //https://firebase.google.com/docs/app-check/android/safetynet-provider?authuser=0#kotlin+ktx

        //Authenticate with Firebase, using the email and password we created earlier//
        val email = getString(R.string.test_email)
        val password = getString(R.string.test_password)

        //Call OnCompleteListener if the user is signed in successfully//
        FirebaseAuth.getInstance().signInWithEmailAndPassword(
            email, password
        ).addOnCompleteListener { task ->
            //If the user has been authenticated...//
            if (task.isSuccessful) {
                //...then call requestLocationUpdates//
                requestLocationUpdates2()
                //Log.d("LoginOK", "requesting updates...")
            } else {
                Toast.makeText(
                    this,
                    "Firebase authentication error. Try again or contact admin",
                    Toast.LENGTH_SHORT
                ).show()
                //If sign in fails, then log the error//
                //Log.e(FIREBASE_AUTH, "Firebase authentication failed")
            }
        }
    }
*/
    //Initiate the request to track the device's location//
    private fun requestLocationUpdates2() {
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

                    //Get a reference to the database, so your app can perform R & W operations//
                    //val ref = FirebaseDatabase.getInstance(path).getReference("users/" + uid
                    //        + "/" + kotlin.math.abs(Random.nextInt()).toString())

                    val location = locationResult.lastLocation
                    //Save the location data to the database//

                    //Creating a location map without unnecessary data
                    //val entry = mapOf("accuracy" to location.accuracy, "latitude"
                    //        to location.latitude, "longitude" to location.longitude,
                    //    "speed" to location.speed, "time" to location.time)

                    Log.d("Location", "Location: $location")
                    //ref.setValue(entry)

                    val realm = Realm.getDefaultInstance()
                    realm.executeTransactionAsync { realm ->
                        val entry = realm.createObject<Location>(ObjectId())
                        entry.accuracy = location.accuracy
                        entry.latitude  = location.latitude
                        entry.longitude = location.longitude
                        entry.speed = location.speed
                        entry.time = location.time
                    }

                }
            }, null)
        }
    }
/*
    //Initiate the request to track the device's location//
    private fun requestLocationUpdates() {
        val request = LocationRequest.create()

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
                    val oref = FirebaseUtils().fireStoreDatabase.document("users/$uid/")
                    oref.set(mapOf("uid" to uid))
                    val ref = FirebaseUtils().fireStoreDatabase.collection(
                        "users/$uid/location/")
                    //Get a reference to the database, so your app can perform R & W operations//
                    //val ref = FirebaseDatabase.getInstance(path).getReference("users/" + uid
                    //        + "/" + kotlin.math.abs(Random.nextInt()).toString())

                    val location = locationResult.lastLocation
                    //Save the location data to the database//

                    //Creating a location map without unnecessary data
                    val entry = mapOf("accuracy" to location.accuracy, "latitude"
                            to location.latitude, "longitude" to location.longitude,
                            "speed" to location.speed, "time" to location.time)

                    //Log.d("Location", "Location: $entry")
                    //ref.setValue(entry)

                    ref.add(entry)
                        .addOnSuccessListener {
                            Log.d("DB", "Added document with ID $uid")
                        }
                }
            }, null)
        }
    }
*/
    companion object {
        private const val CHANNEL_ID = 1945
        //val uid = kotlin.math.abs(Random.nextInt()).toString()
    }
}