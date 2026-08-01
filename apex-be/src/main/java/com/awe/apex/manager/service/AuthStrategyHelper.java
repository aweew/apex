package com.awe.apex.manager.service;

import com.awe.apex.common.constant.enums.GrantTypeEnum;
import com.awe.apex.manager.service.impl.PasswordAuthStrategy;

/**
 * @author Awe
 * @since 2025/12/11 13:17
 */
public class AuthStrategyHelper {

    public static IAuthStrategy getAuthStrategy(GrantTypeEnum grantType) {
        switch (grantType) {
            case PASSWORD:
                return new PasswordAuthStrategy();
            default:
                throw new IllegalArgumentException("Unsupported auth type: " + grantType);
        }
    }

}
