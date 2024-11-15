package com.summersoft.heliocam.repository;

import com.summersoft.heliocam.remote.FirebaseClient;
import com.summersoft.heliocam.utils.DataModel;
import com.summersoft.heliocam.utils.DataModelType;
import com.summersoft.heliocam.utils.ErrorCallback;
import com.summersoft.heliocam.utils.NewEventCallback;
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

    public void sendCallRequest(String target, ErrorCallback errorCallBack){
        firebaseClient.sendMessageToOtherUser(
                new DataModel(target,currentUsername,null, DataModelType.StartCall),errorCallBack
        );
    }


    public void subscribeForLatestEvent(NewEventCallback callBack){
        firebaseClient.observeIncomingLatestEvent(model -> {
            switch (model.getType()){
                case Offer:
                    break;
                case Answer:
                    break;
                case IceCandidate:
                    break;
                case StartCall:
                    callBack.onNewEventReceived(model);
                    break;
            }
        });
    }


}
