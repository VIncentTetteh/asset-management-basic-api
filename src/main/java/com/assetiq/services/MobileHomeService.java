package com.assetiq.services;

import com.assetiq.dto.mobile.MobileHomeResponse;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import org.springframework.security.core.Authentication;

public interface MobileHomeService {

    /**
     * The mobile Home payload for {@code user} in {@code org}. Sections the
     * caller's {@code authentication} does not grant are returned as null.
     */
    MobileHomeResponse getHome(Organisation org, User user, Authentication authentication);
}
