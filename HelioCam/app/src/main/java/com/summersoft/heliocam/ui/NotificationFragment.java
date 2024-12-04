package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.summersoft.heliocam.R;
import com.summersoft.heliocam.notifs.PopulateNotifs;

public class NotificationFragment extends Fragment {

    private static final String TAG = "NotificationFragment";

    public NotificationFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        ViewGroup notificationContainer = view.findViewById(R.id.notifcation_card_container);

        if (getContext() != null) {
            // Create an instance of PopulateNotifs and start the notification population process
            PopulateNotifs populateNotifs = new PopulateNotifs();
            populateNotifs.startPopulatingNotifs(getContext(), notificationContainer);
        } else {
            Log.e(TAG, "Context is null. Unable to start populating notifications.");
        }

        return view;
    }
}
