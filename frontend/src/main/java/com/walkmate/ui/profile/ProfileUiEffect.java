package com.walkmate.ui.profile;

public interface ProfileUiEffect {
    class ShowToast implements ProfileUiEffect {
        private final String message;

        public ShowToast(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    class NavigateBack implements ProfileUiEffect {
    }

    class SaveSuccess implements ProfileUiEffect {
    }
}
