package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.summersoft.heliocam.R;

public class AboutFragment extends Fragment {

    public AboutFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_about, container, false);


        LinearLayout layoutterms = rootView.findViewById(R.id.terms);
        LinearLayout layoutprivacy = rootView.findViewById(R.id.privacy);
        LinearLayout layoutLicense = rootView.findViewById(R.id.licenses);

// Set click listeners for each LinearLayout
        layoutterms.setOnClickListener(v -> {
            // Create an instance of DevicesLoginFragment
           TOSFragment tosFragment = new TOSFragment();

            // Begin a fragment transaction
            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            // Set the custom animations
            transaction.setCustomAnimations(
                    R.anim.slide_in_right,   // Enter animation
                    0
            );

            // Replace the current fragment with DevicesLoginFragment
            transaction.replace(R.id.fragment_container, tosFragment);

            // Optionally add to back stack
            transaction.addToBackStack(null);

            // Commit the transaction
            transaction.commit();
        });
        layoutprivacy.setOnClickListener(v -> {
            // Create an instance of DevicesLoginFragment
            PrivacyFragment privacyFragment = new PrivacyFragment();

            // Begin a fragment transaction
            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            // Set the custom animations
            transaction.setCustomAnimations(
                    R.anim.slide_in_right,   // Enter animation
                    0
            );

            // Replace the current fragment with DevicesLoginFragment
            transaction.replace(R.id.fragment_container, privacyFragment);

            // Optionally add to back stack
            transaction.addToBackStack(null);

            // Commit the transaction
            transaction.commit();
        });


        return rootView;
    }

}
