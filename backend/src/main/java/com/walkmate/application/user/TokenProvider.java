package com.walkmate.application.user;

import com.walkmate.domain.user.User;

public interface TokenProvider {
    TokenPair generateTokenPair(User user);
}
