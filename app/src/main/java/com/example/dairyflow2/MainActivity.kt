package com.example.dairyflow2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dairyflow2.data.model.UserRole
import com.example.dairyflow2.ui.admin.AdminApp
import com.example.dairyflow2.ui.auth.LoginScreen
import com.example.dairyflow2.ui.delivery.DeliveryApp
import com.example.dairyflow2.ui.theme.DairyFlow2Theme
import com.example.dairyflow2.ui.viewmodel.AdminViewModel
import com.example.dairyflow2.ui.viewmodel.AuthViewModel
import com.example.dairyflow2.ui.viewmodel.DeliveryViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels { AuthViewModel.factory(application) }
    private val adminViewModel: AdminViewModel by viewModels { AdminViewModel.factory(application) }
    private val deliveryViewModel: DeliveryViewModel by viewModels { DeliveryViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DairyFlow2Theme {
                val session by authViewModel.session.collectAsStateWithLifecycle()
                val loginState by authViewModel.loginState.collectAsStateWithLifecycle()
                val syncCount by authViewModel.pendingSyncCount.collectAsStateWithLifecycle()
                val current = session
                if (current == null) {
                    LoginScreen(
                        loginState = loginState,
                        onLogin = authViewModel::login,
                        onDemoLogin = authViewModel::loginWithDemo,
                        onCreateAccount = authViewModel::createAccount,
                    )
                } else {
                    when (current.profile.role) {
                        UserRole.Admin -> AdminApp(
                            viewModel = adminViewModel,
                            syncCount = syncCount,
                            onLogout = authViewModel::logout,
                        )

                        UserRole.DeliveryBoy -> DeliveryApp(
                            profile = current.profile,
                            viewModel = deliveryViewModel,
                            syncCount = syncCount,
                            onLogout = authViewModel::logout,
                        )
                    }
                }
            }
        }
    }
}
