package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.summersoft.heliocam.R;

public class AddSessionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_session);

        // Set up the OnApplyWindowInsetsListener to handle system bars insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainpage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Find the ImageButton and set an OnClickListener
        ImageButton imageButtonPhoneCamera = findViewById(R.id.imageButtonPhoneCamera);
        imageButtonPhoneCamera.setOnClickListener(v -> {
            // Start the UsePhoneActivity when the button is clicked
            Intent intent = new Intent(AddSessionActivity.this, UsePhoneActivity.class);
            startActivity(intent);
        });
    }
}
