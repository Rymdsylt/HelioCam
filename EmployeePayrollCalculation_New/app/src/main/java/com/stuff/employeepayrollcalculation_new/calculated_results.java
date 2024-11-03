package com.stuff.employeepayrollcalculation_new;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class calculated_results extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_calculated_results);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Retrieve the intent extras
        String employeeId = getIntent().getStringExtra("employeeId");
        String employeeName = getIntent().getStringExtra("employeeName");
        int daysWorked = getIntent().getIntExtra("daysWorked", 0);
        String positionCode = getIntent().getStringExtra("positionCode");
        String civilStatus = getIntent().getStringExtra("civilStatus");
        double basicPay = getIntent().getDoubleExtra("basicPay", 0);
        double sssContribution = getIntent().getDoubleExtra("sssContribution", 0);
        double withholdingTax = getIntent().getDoubleExtra("withholdingTax", 0);
        double netPay = getIntent().getDoubleExtra("netPay", 0);

        // Initialize TextViews
        TextView textEmployeeId = findViewById(R.id.employee_id);
        TextView textEmployeeName = findViewById(R.id.employee_name);
        TextView textDaysWorked = findViewById(R.id.days_worked);
        TextView textPositionCode = findViewById(R.id.position_code);
        TextView textCivilStatus = findViewById(R.id.civil_status);
        TextView textBasicPay = findViewById(R.id.basic_pay);
        TextView textSSSContribution = findViewById(R.id.sss_contribution);
        TextView textWithholdingTax = findViewById(R.id.witholding_tax);
        TextView textNetPay = findViewById(R.id.net_pay);

        // Set the retrieved data to the TextViews
        textEmployeeId.setText(employeeId);
        textEmployeeName.setText(employeeName);
        textDaysWorked.setText(String.valueOf(daysWorked));
        textPositionCode.setText(positionCode);
        textCivilStatus.setText(civilStatus);
        textBasicPay.setText(String.format("%.2f", basicPay));
        textSSSContribution.setText(String.format("%.2f", sssContribution));
        textWithholdingTax.setText(String.format("%.2f", withholdingTax));
        textNetPay.setText(String.format("%.2f", netPay));

        // Back button listener to return to MainActivity
        findViewById(R.id.compute_button2).setOnClickListener(v -> finish());
    }
}
