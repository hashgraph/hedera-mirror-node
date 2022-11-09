package com.hedera.mirror.web3.evm.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorNodeEvmPropertiesTest {
    private static final String EVM_VERSION = "v0.30";
    private static final int REFUND = 20;

    @Mock
    private MirrorNodeEvmProperties properties;


    @Test
    void correctPropertiesEvaluation() {
        givenValues();
        assertEquals(EVM_VERSION, properties.evmVersion());
        assertFalse(properties.dynamicEvmVersion());
        assertEquals(REFUND, properties.maxGasRefundPercentage());

    }

    private void givenValues() {
        given(properties.dynamicEvmVersion()).willReturn(false);
        given(properties.evmVersion()).willReturn(EVM_VERSION);
        given(properties.maxGasRefundPercentage()).willReturn(REFUND);

    }
}
