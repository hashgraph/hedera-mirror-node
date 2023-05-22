package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.services.store.contracts.precompile.Precompile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class PrecompileFactoryTest {

    private PrecompileFactory subject;

    @BeforeEach
    void setUp() {
        subject = new PrecompileFactory(Set.of(new MockPrecompile()));
    }

    @Test
    void nonExistingAbiReturnsNull() {
        int functionSelector = 0x11111111;
        final Precompile result = subject.lookup(functionSelector);
        assertThat(result).isNull();
    }
}
