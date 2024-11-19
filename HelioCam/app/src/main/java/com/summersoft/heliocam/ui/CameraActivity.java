package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.widget.Toast;
import android.view.View;

import javax.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.summersoft.heliocam.databinding.ActivityCameraBinding;
import com.summersoft.heliocam.repository.MainRepository;
import com.summersoft.heliocam.status.LoginStatus;
import com.summersoft.heliocam.utils.DataModelType;


public class CameraActivity extends AppCompatActivity {

    private ActivityCameraBinding views;
    private MainRepository mainRepository;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        views = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        LoginStatus.checkLoginStatus(this);

        init();
    }

    private void init(){
        mainRepository = MainRepository.getInstance();
        views.callBtn.setOnClickListener(v->{
            //start a call a request
            mainRepository.sendCallRequest(views.targetUserNameEt.getText().toString(),()->{
                Toast.makeText(this,"couldn't find target", Toast.LENGTH_SHORT);
            });
        });

        mainRepository.subscribeForLatestEvent(data->{
            if (data.getType()== DataModelType.StartCall){
                runOnUiThread(()->{
                    views.incomingNameTV.setText(data.getSender()+" is Calling you");
                    views.incomingCallLayout.setVisibility(View.VISIBLE);
                    views.acceptButton.setOnClickListener(v->{
                        //star the call here

                        views.incomingCallLayout.setVisibility(View.GONE);
                    });
                    views.rejectButton.setOnClickListener(v->{
                        views.incomingCallLayout.setVisibility(View.GONE);
                    });
                });
            }
        });


    }
}
