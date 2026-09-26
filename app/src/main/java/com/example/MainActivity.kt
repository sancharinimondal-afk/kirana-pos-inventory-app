package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.backup.AutoBackupScheduler
import com.example.data.AppDatabase
import com.example.data.KiranaRepository
import com.example.ui.KiranaApp
import com.example.ui.KiranaViewModel
import com.example.ui.KiranaViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private val viewModel: KiranaViewModel by viewModels {
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = KiranaRepository(database)
    KiranaViewModelFactory(repository, applicationContext)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize daily background automatic backup scheduler
    AutoBackupScheduler.scheduleDailyBackup(applicationContext)

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          KiranaApp(viewModel = viewModel)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    com.example.security.SecureAuthManager.checkAutoLock(applicationContext)
  }

  override fun onPause() {
    super.onPause()
    com.example.security.SecureAuthManager.recordUserActivity(applicationContext)
  }
}
