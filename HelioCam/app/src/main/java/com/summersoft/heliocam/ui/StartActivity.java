package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.summersoft.heliocam.R;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);


        new Handler().postDelayed(() -> {

            boolean isLoggedIn = checkUserLoginStatus();


            Intent intent = new Intent(StartActivity.this,
                    isLoggedIn ? HomeActivity.class : LoginActivity.class);
            startActivity(intent);
            finish();
        }, 3000);
    }

    private boolean checkUserLoginStatus() { //FOR LATER USE, AUTOMATICALLY PROCEED TO SESSION MANAGER IF PREVIOUSLY LOGGED IN
        return getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .getBoolean("isLoggedIn", false);
    }
}
