package com.walkmate.domain.user;

public class User {
    private final String id;
    private final String fullname;
    private final String email;

    public User(String id, String fullname, String email) {
        this.id = id;
        this.fullname = fullname;
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public String getFullname() {
        return fullname;
    }

    public String getEmail() {
        return email;
    }
}
