package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.services.store.contracts.precompile.Precompile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class PrecompileFactoryTest {

    private PrecompileFactory subject;

    @BeforeEach
    void setUp() {
        subject = new PrecompileFactory();
    }

    @Test
    void nonExistingAbiReturnsNull() {
        int functionSelector = 0x00000000;
        final Precompile result = subject.lookup(functionSelector);
        assertThat(result).isNull();
    }

    @Test
    void noMatchingAbiThrowsException() {
        int functionSelector = 0x00000001;

        assertThatThrownBy(() -> subject.lookup(functionSelector)).isInstanceOf(UnsupportedOperationException.class).hasMessage("Precompile not supported for non-static frames");
    }
}
