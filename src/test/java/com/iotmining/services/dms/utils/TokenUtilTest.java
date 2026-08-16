package com.iotmining.services.dms.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUtilTest {

    @Test
    void generatesTokenOfRequestedLength() {
        assertThat(TokenUtil.generateDeviceToken(16)).hasSize(16);
        assertThat(TokenUtil.generateDeviceToken(32)).hasSize(32);
    }

    @Test
    void usesOnlyAlphanumericCharacters() {
        String token = TokenUtil.generateDeviceToken(256);
        assertThat(token).matches("[A-Za-z0-9]+");
    }

    @Test
    void tokensAreEffectivelyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(TokenUtil.generateDeviceToken(16));
        }
        assertThat(seen).hasSize(500);
    }
}
