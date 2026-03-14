package com.walkmate.ui.register;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {
    public String fullname;

    public String email;
    public String password;

    public RegisterRequest(String fullname, String email, String password){
        this.fullname = fullname;
        this.email = email;
        this.password = password;
    }
}
