package com.walkmate.ui.history;

public class Session {
    private String day;
    private String month;
    private int avatarResId;
    private String partnerName;
    private String status;
    private String location;
    private String type;
    private String duration;
    private String distance;
    private String steps;
    private int rating;
    private String alertMessage;

    public Session(String day, String month, int avatarResId, String partnerName, String status, String location, String type, String duration, String distance, String steps, int rating, String alertMessage) {
        this.day = day;
        this.month = month;
        this.avatarResId = avatarResId;
        this.partnerName = partnerName;
        this.status = status;
        this.location = location;
        this.type = type;
        this.duration = duration;
        this.distance = distance;
        this.steps = steps;
        this.rating = rating;
        this.alertMessage = alertMessage;
    }

    public String getDay() { return day; }
    public String getMonth() { return month; }
    public int getAvatarResId() { return avatarResId; }
    public String getPartnerName() { return partnerName; }
    public String getStatus() { return status; }
    public String getLocation() { return location; }
    public String getType() { return type; }
    public String getDuration() { return duration; }
    public String getDistance() { return distance; }
    public String getSteps() { return steps; }
    public int getRating() { return rating; }
    public String getAlertMessage() { return alertMessage; }
}
