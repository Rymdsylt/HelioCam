package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        LoginStatus.checkLoginStatus(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        View hamburgerButton = findViewById(R.id.hamburgerButton);
        registerForContextMenu(hamburgerButton);

        hamburgerButton.setOnClickListener(v -> {
            Log.d(TAG, "Hamburger button clicked!");
            v.showContextMenu();
        });
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
                logoutUser();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void logoutUser() {

        FirebaseAuth.getInstance().signOut();
        Log.d(TAG, "User logged out.");
        LoginStatus.checkLoginStatus(this);
        finish();
    }
}
