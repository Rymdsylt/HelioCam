package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.summersoft.heliocam.databinding.ActivityLoginBinding;


import com.summersoft.heliocam.R;
import com.summersoft.heliocam.repository.MainRepository;


public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding views;

    private MainRepository mainRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        views = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        init();
    }

    private void init(){
        mainRepository = MainRepository.getInstance();
        views.enterBtn.setOnClickListener(v->{
            //login
            mainRepository.login(
              views.username.getText().toString(),()->{
                  //success
                  startActivity(new Intent(LoginActivity.this, CameraActivity.class));
                    });

        });
    }
}