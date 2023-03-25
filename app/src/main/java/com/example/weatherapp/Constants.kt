package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Constants {
    const val APP_ID = "b765a9d291f403838aa6b1a23dce92bf"
    const val BASE_URL = "https://api.openweathermap.org/data/"
    const val METRIC_UNIT = "metric"
    const val PREFERENCE_NAME = "WeatherAppPreference"
    const val WEATHER_RESPONSE_DATA = "weather_response_data"

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCapabilities =
            connectivityManager.activeNetwork ?: return false

        val activeNetwork = connectivityManager.getNetworkCapabilities(
            networkCapabilities) ?: return false

        return when {
            activeNetwork.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(
                NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}