package com.summersoft.heliocam.utils;

public class UserModel {
    private String fullname;
    private String username;
    private String contact;
    private String email;

    // Default constructor required by Firebase
    public UserModel() {
    }

    public UserModel(String fullname, String username, String contact, String email) {
        this.fullname = fullname;
        this.username = username;
        this.contact = contact;
        this.email = email;
    }

    // Getters and setters for each field
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
    }    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
