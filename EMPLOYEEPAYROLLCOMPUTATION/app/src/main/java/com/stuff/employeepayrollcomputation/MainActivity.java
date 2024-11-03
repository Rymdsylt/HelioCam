package com.stuff.employeepayrollcomputation;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final String DATABASE_NAME = "EmployeeData.db";
    private static final String TAG = "MainActivity"; // Tag for logging
    private SQLiteDatabase employeeDatabase;
    private Spinner spinnerEmployeeId;
    private TextView textEmployeeName;

    // Store employee IDs and names for easy access
    private HashMap<String, String> employeeDataMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.spinner_employee_id), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize the database
        try {
            employeeDatabase = openOrCreateDatabase(DATABASE_NAME, MODE_PRIVATE, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open or create database", e);
            return; // Early exit if database creation fails
        }

        // Set up UI references
        spinnerEmployeeId = findViewById(R.id.spinner_employee_id);
        textEmployeeName = findViewById(R.id.text_employee_name);

        // Load employee data
        loadEmployeeData();

        // Set up spinner listener
        spinnerEmployeeId.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                // Get selected employee ID
                String selectedEmployeeId = (String) parent.getItemAtPosition(position);
                // Display corresponding employee name
                textEmployeeName.setText(employeeDataMap.get(selectedEmployeeId));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                textEmployeeName.setText("");
            }
        });
    }

    private void loadEmployeeData() {
        Cursor cursor = null;
        try {
            // Query to retrieve employee IDs and names
            cursor = employeeDatabase.rawQuery("SELECT EmployeeID, EmployeeName FROM EmployeeDetails", null);

            // Ensure cursor is not null and has data
            if (cursor != null && cursor.getCount() > 0) {
                // Check if column names exist to avoid -1 values
                int employeeIdIndex = cursor.getColumnIndex("EmployeeID");
                int employeeNameIndex = cursor.getColumnIndex("EmployeeName");

                // Log column index for debugging
                Log.d(TAG, "Employee ID Index: " + employeeIdIndex);
                Log.d(TAG, "Employee Name Index: " + employeeNameIndex);

                if (employeeIdIndex == -1 || employeeNameIndex == -1) {
                    throw new IllegalArgumentException("Column names do not match in the database.");
                }

                ArrayList<String> employeeIds = new ArrayList<>();

                // Populate the employee data map and the list of IDs
                while (cursor.moveToNext()) {
                    String employeeId = cursor.getString(employeeIdIndex);
                    String employeeName = cursor.getString(employeeNameIndex);
                    employeeIds.add(employeeId);
                    employeeDataMap.put(employeeId, employeeName);

                    // Log retrieved employee data for debugging
                    Log.d(TAG, "Retrieved Employee ID: " + employeeId + ", Name: " + employeeName);
                }

                // Log the total count of IDs retrieved
                Log.d(TAG, "Total Employee IDs retrieved: " + employeeIds.size());

                // Set up the spinner with the employee IDs
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, employeeIds);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerEmployeeId.setAdapter(adapter);
            } else {
                Log.w(TAG, "No employee data found in the database.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading employee data", e);
        } finally {
            if (cursor != null) {
                cursor.close(); // Ensure cursor is closed in the finally block
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (employeeDatabase != null && employeeDatabase.isOpen()) {
            employeeDatabase.close();
        }
    }
}
