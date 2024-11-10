package com.summersoft.heliocam.repository;

import com.summersoft.heliocam.remote.FirebaseClient;
import com.summersoft.heliocam.utils.SuccessCallback;

public class MainRepository {

    private FirebaseClient firebaseClient;
    private String currentUsername;

    private void updateCurrentUsername(String username){
        this.currentUsername = username;
    }

    private MainRepository(){
       this.firebaseClient = new FirebaseClient();
    }

    private static MainRepository instance;

    public static MainRepository getInstance(){
        if(instance == null){
            instance = new MainRepository();

        }
        return instance;
    }

    public void login(String username, SuccessCallback callBack){
        firebaseClient.login(username,()-> {

            callBack.onSuccess();
        });

    }


}
