package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.content.Context;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;
import com.summersoft.heliocam.status.LogoutUser;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private FirebaseAuth mAuth;
    private Handler handler = new Handler();
    private Runnable checkLoginStatusRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();

        // Apply system bars padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Register for the context menu on hamburger button
        View hamburgerButton = findViewById(R.id.hamburgerButton);
        registerForContextMenu(hamburgerButton);

        hamburgerButton.setOnClickListener(v -> {
            Log.d(TAG, "Hamburger button clicked!");
            v.showContextMenu();
        });

        // Runnable to call LoginStatus.checkLoginStatus() every 1.5 seconds
        checkLoginStatusRunnable = new Runnable() {
            @Override
            public void run() {
                LoginStatus.checkLoginStatus(HomeActivity.this); // Check login status
                handler.postDelayed(this, 1500); // Repeat every 1.5 seconds
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start the periodic check
        handler.postDelayed(checkLoginStatusRunnable, 1500); // Start after 1.5 seconds
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop the periodic check to avoid memory leaks
        handler.removeCallbacks(checkLoginStatusRunnable);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (v.getId() == R.id.hamburgerButton) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_logout:
                LogoutUser.logoutUser();  // Pass the Context here
                LoginStatus.checkLoginStatus(this);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
}
