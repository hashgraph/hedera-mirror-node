package com.hedera.mirror.web3.service.eth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EthCallServiceTest {
    private EthRpcCallBody body;
    @InjectMocks
    private EthCallService ethCallService;

    @BeforeEach
    public void populateBody() {
        body = new EthRpcCallBody("0", "0x6f0fccab00000000000000000000000000000000000000000000000000000000000004e5",
                "0x00000000000000000000000000000000000004e2",
                "0x76c0", "0x76c0", "0x00000000000000000000000000000000000004e4", "0");
    }

    @Test
    void get() {
        final var success = "0x0000000000000000000000000000000000000000000000000000000000000016";
        final var result = ethCallService.get(body);
        Assertions.assertEquals(success, result);
    }
}
