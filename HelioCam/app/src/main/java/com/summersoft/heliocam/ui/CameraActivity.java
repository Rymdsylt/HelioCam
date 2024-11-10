package com.summersoft.heliocam.ui;

import android.os.Bundle;

import javax.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.summersoft.heliocam.databinding.ActivityCameraBinding;



public class CameraActivity extends AppCompatActivity {

    private ActivityCameraBinding views;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        views = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        init();
    }

    private void init(){


    }
}
