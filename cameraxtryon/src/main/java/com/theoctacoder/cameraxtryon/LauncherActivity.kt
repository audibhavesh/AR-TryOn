package com.theoctacoder.cameraxtryon

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.permissionx.guolindev.PermissionX
import com.theoctacoder.cameraxtryon.databinding.ActivityLauncherBinding

class LauncherActivity : AppCompatActivity() {
    lateinit var binding: ActivityLauncherBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (!checkPermissions()) {
            requestPermissionForAR()
        } else {
            startNextScreen()
        }
        binding.launchAR.setOnClickListener {
            if (checkPermissions()) {
                startNextScreen()
            } else {
                requestPermissionForAR()
            }
        }

    }

    private fun startNextScreen() {
        startActivity(Intent(this, PoseTryOnActivity::class.java))
    }

    private fun requestPermissionForAR() {
        PermissionX.init(this)
            .permissions(android.Manifest.permission.CAMERA)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "Core fundamental are based on these permissions",
                    "OK",
                    "Cancel"
                )
            }
            .onForwardToSettings { scope, deniedList ->
//                scope.showForwardToSettingsDialog(
//                    deniedList,
//                    "You need to allow necessary permissions in Settings manually",
//                    "OK",
//                    "Cancel"
//                )
            }
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {

                    Toast.makeText(this, "All permissions are granted", Toast.LENGTH_LONG).show()
//                    initializeMediaPipe()
//                    initializeAR()
                } else {
                    Toast.makeText(
                        this,
                        "These permissions are denied: $deniedList",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }


    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}