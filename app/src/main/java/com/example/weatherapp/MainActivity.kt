package com.example.weatherapp

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    private val locationCallback = object: LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation = locationResult.lastLocation

            val latitude = lastLocation?.latitude ?: 0.0
            Log.i("Current Latitude:", "$latitude")

            val longitude = lastLocation?.longitude ?: 0.0
            Log.i("Current longitude:", "$longitude")

            getLocationWeatherDetail(latitude, longitude)
        }
    }

    private var progressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this@MainActivity,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)

        } else {
            Dexter.withContext(this@MainActivity).
            withPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).withListener(object: MultiplePermissionsListener {
                override fun onPermissionsChecked(
                    report: MultiplePermissionsReport?) {

                    if (report!!.areAllPermissionsGranted()) requestLocationData()

                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "You have denied location permissions.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationaleDialogForPermissions()
                }
            }).onSameThread().check()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1)
            .setMaxUpdates(1)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.myLooper())
    }

    private fun getLocationWeatherDetail(latitude: Double, longitude: Double) {

        if (Constants.isNetworkAvailable(this@MainActivity)) {

            val retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)

            val listCall = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)

            showProgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        hideProgressDialog()

                        val weatherList: WeatherResponse = response.body()!!
                        val weatherResponseJsonString = Gson().toJson(weatherList)

                        val editor = sharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response Result", "$weatherList")

                    } else {
                        when(response.code()) {
                            400 -> Log.e("Error 400", "Bad connection")
                            404 -> Log.e("Error 404", "Not Found")
                            else -> Log.e("Error", "Generic Error")
                        }
                    }
                }

                override fun onFailure(
                    call: Call<WeatherResponse>,
                    t: Throwable
                ) {
                    Log.e("Failure", t.message.toString())
                    hideProgressDialog()
                }
            })

        } else {
            Toast.makeText(this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationaleDialogForPermissions() {
        AlertDialog.Builder(this@MainActivity)
            .setMessage("It looks like you have turned off permissions " +
                    "required for this feature. It can be enabled under " +
                    "Application Settings")
            .setPositiveButton("GO TO SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)

                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("CANCEL") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun showProgressDialog() {
        progressDialog = Dialog(this@MainActivity)
        progressDialog?.setContentView(R.layout.dialog_custom_progress)
        progressDialog?.show()
    }

    private fun hideProgressDialog() = progressDialog?.dismiss()

    private fun setupUI() {

        val weatherResponseJsonString = sharedPreferences
            .getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList = Gson()
                .fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices) {
                binding.tvMain.text = weatherList.weather[i].main
                binding.tvMainCondition.text = weatherList.weather[i].description

                val temperatureUnit = getUnit(
                    application.resources.configuration.locales.toString())

                binding.tvTemp.text =
                    String.format("%.1f", weatherList.main.temp) + temperatureUnit

                binding.tvHumidity.text = weatherList.main.humidity.toString() + "%"

                binding.tvTempMax.text = String.format(
                    "%.1f", weatherList.main.temp_max) + temperatureUnit
                binding.tvTempMin.text = String.format(
                    "%.1f", weatherList.main.temp_min) + temperatureUnit

                binding.tvSpeed.text = weatherList.wind.speed.toString()

                binding.tvCityName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country

                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)

                binding.ivMain.setImageResource(
                    when(weatherList.weather[i].icon) {
                        "01d", "01n" -> R.drawable.sunny
                        "09d", "09n", "10d", "10n" -> R.drawable.rain
                        "11d", "11n" -> R.drawable.storm
                        "13d", "13n" -> R.drawable.snowflake
                        else -> R.drawable.cloud
                    }
                )
            }
        }


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getUnit(value: String) =
        if ("US" == value || "LR" == value || "MM" == value) "°F"  else "°C"

    private fun unixTime(timeLong: Long): String {
        val date = Date(timeLong * 1000)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(date)
    }
}