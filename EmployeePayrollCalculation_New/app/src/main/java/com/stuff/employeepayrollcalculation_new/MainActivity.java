package com.stuff.employeepayrollcalculation_new;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final String DATABASE_NAME = "EmployeeData.db";
    private SQLiteDatabase employeeDatabase;
    private Spinner spinnerEmployeeId;
    private TextView textEmployeeName;
    private ArrayList<String> employeeIds;
    private HashMap<String, String> employeeMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        spinnerEmployeeId = findViewById(R.id.spinner);
        textEmployeeName = findViewById(R.id.textView7);
        Button computeButton = findViewById(R.id.compute_button);
        EditText daysWorkedInput = findViewById(R.id.input_text_worked);
        Spinner positionCodeSpinner = findViewById(R.id.spinner_position_code);
        RadioGroup civilStatusGroup = findViewById(R.id.radioGroup);


        employeeDatabase = openOrCreateDatabase(DATABASE_NAME, MODE_PRIVATE, null);
        createEmployeeTable();
        addInitialEmployees();
        populateEmployeeSpinner();

        spinnerEmployeeId.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedId = employeeIds.get(position);
                String employeeName = employeeMap.get(selectedId);
                textEmployeeName.setText(employeeName != null ? employeeName : "Unknown");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                textEmployeeName.setText("");
            }
        });



        computeButton.setOnClickListener(v -> {
            String selectedId = spinnerEmployeeId.getSelectedItem().toString();
            int daysWorked;
            try {
                daysWorked = Integer.parseInt(daysWorkedInput.getText().toString());
            } catch (NumberFormatException e) {
                Toast.makeText(MainActivity.this, "Please enter valid days worked", Toast.LENGTH_SHORT).show();
                return;
            }


            int selectedCivilStatusId = civilStatusGroup.getCheckedRadioButtonId();
            if (selectedCivilStatusId == -1) {
                Toast.makeText(MainActivity.this, "Please select a civil status", Toast.LENGTH_SHORT).show();
                return;
            }

            String civilStatus = selectedCivilStatusId == R.id.radio_single ? "Single" : "Married";

            try {

                String employeeName = employeeMap.get(selectedId);


                String selectedPositionCode = positionCodeSpinner.getSelectedItem().toString();


                double ratePerDay = 500;
                double taxRate = selectedCivilStatusId == R.id.radio_single ? 0.10 : 0.15;
                double sssRate = 0.07;

                double basicPay = daysWorked * ratePerDay;
                double sssContribution = basicPay * sssRate;
                double withholdingTax = basicPay * taxRate;
                double netPay = basicPay - (sssContribution + withholdingTax);


                Intent intent = new Intent(MainActivity.this, calculated_results.class);
                intent.putExtra("employeeId", selectedId);
                intent.putExtra("employeeName", employeeName);
                intent.putExtra("daysWorked", daysWorked);
                intent.putExtra("positionCode", selectedPositionCode);
                intent.putExtra("civilStatus", civilStatus);
                intent.putExtra("basicPay", basicPay);
                intent.putExtra("sssContribution", sssContribution);
                intent.putExtra("withholdingTax", withholdingTax);
                intent.putExtra("netPay", netPay);


                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Error in calculation: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        Button clearButton = findViewById(R.id.clear_button);

        clearButton.setOnClickListener(v -> {

            textEmployeeName.setText("");


            daysWorkedInput.setText("");


            civilStatusGroup.clearCheck();


            positionCodeSpinner.setSelection(0);


            spinnerEmployeeId.setSelection(0);
        });








        ArrayAdapter<String> positionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"a", "b", "c"});
        positionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        positionCodeSpinner.setAdapter(positionAdapter);
    }

    private void populateEmployeeSpinner() {
        employeeIds = new ArrayList<>();
        employeeMap = new HashMap<>();

        Cursor cursor = employeeDatabase.rawQuery("SELECT EmployeeID, EmployeeName FROM EmployeeDetails", null);
        if (cursor.moveToFirst()) {
            do {
                String employeeId = cursor.getString(0);
                String employeeName = cursor.getString(1);
                employeeIds.add(employeeId);
                employeeMap.put(employeeId, employeeName);
            } while (cursor.moveToNext());
        }
        cursor.close();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, employeeIds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEmployeeId.setAdapter(adapter);
    }

    private void createEmployeeTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS EmployeeDetails (" +
                "EmployeeID TEXT PRIMARY KEY, " +
                "EmployeeName TEXT NOT NULL);";
        employeeDatabase.execSQL(createTableQuery);
    }

    private void addInitialEmployees() {

        String[] employeeNames = {"Miguel", "Mikoyan", "Alyosha", "Andrev", "Jammpi"};
        for (int i = 0; i < employeeNames.length; i++) {
            String employeeID = String.format("emp%03d", i + 1);
            String insertQuery = "INSERT OR IGNORE INTO EmployeeDetails (EmployeeID, EmployeeName) VALUES (?, ?)";
            try {
                employeeDatabase.execSQL(insertQuery, new Object[]{employeeID, employeeNames[i]});
            } catch (Exception e) {

                Toast.makeText(this, "Error adding employee: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
