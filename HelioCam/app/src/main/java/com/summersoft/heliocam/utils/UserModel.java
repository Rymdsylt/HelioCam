package com.summersoft.heliocam.utils;

public class UserModel {
    private String fullname, username, contact, email;

    // Constructor with only 4 parameters
    public UserModel(String fullname, String username, String contact, String email) {
        this.fullname = fullname;
        this.username = username;
        this.contact = contact;
        this.email = email;
    }

    // Getters and setters (if needed for Firebase Realtime Database)
    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
