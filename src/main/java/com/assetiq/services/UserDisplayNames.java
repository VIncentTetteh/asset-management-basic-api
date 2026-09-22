package com.assetiq.services;

import com.assetiq.models.User;

/** How approval trails name a user: full name, else email. */
public final class UserDisplayNames {

    private UserDisplayNames() {
    }

    /** The user's full name, or their email when no name is on file; null for no user. */
    public static String of(User user) {
        if (user == null) return null;
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
