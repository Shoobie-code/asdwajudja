package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.skyblockminer.config.MinerConfig;
import org.junit.jupiter.api.Test;

class SolversTest {
    @Test
    void staysOutWhileAMacroIsBusy() {
        // Minecraft is null here: a busy solver must return before it reads any game state.
        Solvers solvers = new Solvers(new MinerConfig(), () -> true);
        assertDoesNotThrow(() -> solvers.tick(null));
    }
}
