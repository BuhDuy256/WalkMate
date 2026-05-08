package com.walkmate.ui.walkpost;

import com.walkmate.domain.walkpost.WalkPost;

import java.util.Collections;
import java.util.List;

public class WalkActivityUiState {

    public enum Kind { LOADING, READY, ERROR }

    public final Kind kind;
    public final List<WalkPost> posts;
    public final String error;

    private WalkActivityUiState(Kind kind, List<WalkPost> posts, String error) {
        this.kind  = kind;
        this.posts = posts != null ? posts : Collections.emptyList();
        this.error = error;
    }

    public static WalkActivityUiState loading() {
        return new WalkActivityUiState(Kind.LOADING, null, null);
    }

    public static WalkActivityUiState ready(List<WalkPost> posts) {
        return new WalkActivityUiState(Kind.READY, posts, null);
    }

    public static WalkActivityUiState error(String message) {
        return new WalkActivityUiState(Kind.ERROR, null, message);
    }
}
