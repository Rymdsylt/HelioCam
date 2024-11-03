package com.stuff.employeepayrollcomputation;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.*;

import androidx.annotation.Nullable;

public class PayrollDatabase extends SQLiteOpenHelper {


    public PayrollDatabase(Context context) {
        super(context, "EmployeeData.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase DB) {
            DB.execSQL("create Table EmployeeDetails(name Text primary key)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase DB, int oldVersion, int newVersion) {

        DB.execSQL("drop Table if exists EmployeeDetails");

    }

    public Boolean insertuserdata(String name, String employeeID) {

        SQLiteDatabase DB = this.getWritableDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put("employeeID", employeeID);
        contentValues.put("name", name);


        long result = DB.insert("EmployeeDetails", null, contentValues);
        if (result == -1) {
            return false;
        }
        else {
            return true;
        }
    }

}
