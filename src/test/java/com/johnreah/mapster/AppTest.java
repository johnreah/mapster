package com.johnreah.mapster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppTest {

    @Test
    void appShouldInstantiate() {
        App app = new App();
        assertNotNull(app);
    }
}
