package com.hedera.mirror.web3.evm.properties;

import static org.junit.jupiter.api.Assertions.*;

import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorNodeEvmPropertiesTest {
    private static final String EVM_VERSION = "v0.30";
    private static final int REFUND = 20;
    private static final Address ADDRESS = Address.fromHexString("0x4e4");

    private MirrorNodeEvmProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MirrorNodeEvmProperties();
    }

    @Test
    void correctPropertiesEvaluation() {
        givenValues();
        assertEquals(EVM_VERSION, properties.evmVersion());
        assertFalse(properties.dynamicEvmVersion());
        assertEquals(REFUND, properties.maxGasRefundPercentage());
        assertEquals(ADDRESS, properties.fundingAccountAddress());
    }

    private void givenValues() {
        properties.setEvmVersion(EVM_VERSION);
        properties.setDynamicEvmVersion(false);
        properties.setFundingAccount(ADDRESS);
        properties.setMaxGasRefundPercentage(REFUND);
    }
}
