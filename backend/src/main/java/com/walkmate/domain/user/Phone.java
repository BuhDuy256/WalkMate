package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;

/**
 * Value object for a phone number in E.164 format (+[country][number], 8–15 digits).
 */
public record Phone(String value) {

    private static final java.util.regex.Pattern E164 =
            java.util.regex.Pattern.compile("^\\+[1-9]\\d{7,14}$");

    public Phone {
        if (value == null || !E164.matcher(value).matches()) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA,
                    "Phone must be in E.164 format (e.g. +84901234567)");
        }
    }
}
