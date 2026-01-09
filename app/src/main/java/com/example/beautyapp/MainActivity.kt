package com.example.beautyapp

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.beautyapp.ui.CameraScreen
import com.example.beautyapp.ui.SettingsScreen
import com.example.beautyapp.ui.YoloObjectListScreen
import com.example.beautyapp.ui.ModelManagementScreen
import com.example.beautyapp.viewmodel.BeautyViewModel
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed")
        } else {
            Log.d("OpenCV", "OpenCV initialization success")
        }

        setContent {
            val viewModel: BeautyViewModel = viewModel()
            BeautyAppTheme(viewModel) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: BeautyViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(navController, viewModel)
        }
        composable("settings") {
            SettingsScreen(navController, viewModel)
        }
        composable("yolo_objects") {
            YoloObjectListScreen(navController, viewModel)
        }
        composable("model_management") {
            ModelManagementScreen(navController, viewModel)
        }
    }
}

@Composable
fun BeautyAppTheme(viewModel: BeautyViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Dynamic Color (Monet) logic
    val colorScheme = if (viewModel.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (viewModel.isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (viewModel.isDarkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
