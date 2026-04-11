package com.walkmate.domain.profile;

public class ProfileAvatarUpload {
    private final String fileName;
    private final String mimeType;
    private final byte[] bytes;

    public ProfileAvatarUpload(String fileName, String mimeType, byte[] bytes) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.bytes = bytes;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
