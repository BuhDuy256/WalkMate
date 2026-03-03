package com.walkingapp.model;

import com.google.gson.annotations.SerializedName;

public class Intent {
  @SerializedName("id")
  private Integer id;

  @SerializedName("user_id")
  private Integer userId;

  @SerializedName("walk_type")
  private String walkType;

  @SerializedName("start_at")
  private String startAt;

  @SerializedName("flex_minutes")
  private Integer flexMinutes;

  @SerializedName("window_start")
  private String windowStart;

  @SerializedName("window_end")
  private String windowEnd;

  @SerializedName("lat")
  private Double lat;

  @SerializedName("lng")
  private Double lng;

  @SerializedName("radius_m")
  private Integer radiusM;

  @SerializedName("status")
  private String status;

  @SerializedName("created_at")
  private String createdAt;

  @SerializedName("updated_at")
  private String updatedAt;

  public Intent() {
  }

  public Intent(String walkType, String startAt, Integer flexMinutes, Double lat, Double lng, Integer radiusM) {
    this.walkType = walkType;
    this.startAt = startAt;
    this.flexMinutes = flexMinutes;
    this.lat = lat;
    this.lng = lng;
    this.radiusM = radiusM;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public String getWalkType() {
    return walkType;
  }

  public void setWalkType(String walkType) {
    this.walkType = walkType;
  }

  public String getStartAt() {
    return startAt;
  }

  public void setStartAt(String startAt) {
    this.startAt = startAt;
  }

  public Integer getFlexMinutes() {
    return flexMinutes;
  }

  public void setFlexMinutes(Integer flexMinutes) {
    this.flexMinutes = flexMinutes;
  }

  public String getWindowStart() {
    return windowStart;
  }

  public void setWindowStart(String windowStart) {
    this.windowStart = windowStart;
  }

  public String getWindowEnd() {
    return windowEnd;
  }

  public void setWindowEnd(String windowEnd) {
    this.windowEnd = windowEnd;
  }

  public Double getLat() {
    return lat;
  }

  public void setLat(Double lat) {
    this.lat = lat;
  }

  public Double getLng() {
    return lng;
  }

  public void setLng(Double lng) {
    this.lng = lng;
  }

  public Integer getRadiusM() {
    return radiusM;
  }

  public void setRadiusM(Integer radiusM) {
    this.radiusM = radiusM;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }
}
