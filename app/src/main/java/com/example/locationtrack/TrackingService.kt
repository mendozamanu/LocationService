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
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.bson.Document



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
            //uiThreadRealm.close()
            unregisterReceiver(this)

            //Revisar el IntentReceiver de Realm

            //Stop the Service//
            stopSelf()
            //Log.d("Stopped", "Service stopped")
            // Optional: Android should manage itself -> no need to use exitProcess(0)

        }
    }

    private fun loginToMongo(){

        val connectionString =
            ConnectionString("mongodb://150.214.117.48:27017/todo?retryWrites=true&w=majority")
        Log.d("Connect to", connectionString.toString())
        val settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applicationName("location")
            .build()
        val mongoClient: MongoClient = MongoClients.create(settings)

        val database: MongoDatabase = mongoClient.getDatabase("todo")

        /*val pojoCodecRegistry = CodecRegistries.fromRegistries(
            AppConfiguration.DEFAULT_BSON_CODEC_REGISTRY,
            CodecRegistries.fromProviders(
                PojoCodecProvider.builder().automatic(true).build()))
*/
        val mongoCollection =
            database.getCollection(
                "Location")//, Location::class.java).withCodecRegistry(pojoCodecRegistry)
        Log.v("EXAMPLE", "Successfully instantiated the MongoDB collection handle")

        requestLocationUpdates2(mongoCollection)

    }

    //Initiate the request to track the device's location//
    private fun requestLocationUpdates2(mongoCollection: MongoCollection<Document>) {
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

                    val policy = ThreadPolicy.Builder().permitAll().build()

                    StrictMode.setThreadPolicy(policy)

                    //Get a reference to the database, so your app can perform R & W operations//
                    //val ref = FirebaseDatabase.getInstance(path).getReference("users/" + uid
                    //        + "/" + kotlin.math.abs(Random.nextInt()).toString())

                    val location = locationResult.lastLocation
                    //Save the location data to the database//

                    //Creating a location map without unnecessary data
                    val entry = mapOf("accuracy" to location.accuracy, "latitude"
                            to location.latitude, "longitude" to location.longitude,
                           "speed" to location.speed, "time" to location.time)

                    Log.d("Location", "Location: $location")

                    //ref.setValue(entry)
                    val inserted = mongoCollection.insertOne(Document(entry))
                    Log.d("INSERTED", inserted.toString())

                }
            }, null)
        }
    }

    companion object {
        private const val CHANNEL_ID = 1945
        //val uid = kotlin.math.abs(Random.nextInt()).toString()
    }
}