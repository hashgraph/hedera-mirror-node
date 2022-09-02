package com.hedera.mirror.web3.evm;

import com.hedera.mirror.web3.ApiContractIntegrationTest;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.EntityRepository;

import org.hyperledger.besu.evm.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.annotation.Resource;

import static com.hedera.mirror.web3.utils.TestConstants.contractAddress;
import static com.hedera.mirror.web3.utils.TestConstants.runtimeByteCodeBytes;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class CodeCacheTest extends ApiContractIntegrationTest {

    @Resource
    ContractRepository contractRepository;

    @Resource
    EntityRepository entityRepository;

    @InjectMocks
    private CodeCache codeCache;

    @BeforeEach
    void setup() {
        codeCache = new CodeCache(contractRepository, entityRepository);
    }

    @Test
    void successfulCreate() {
        assertNotEquals(Code.EMPTY, codeCache.getIfPresent(contractAddress));
    }

    @Test
    void validateContractBytecode() {
        final var runtimeBytecode = codeCache.getIfPresent(contractAddress).getBytes().toString();

        assertEquals(runtimeByteCodeBytes.toString(), runtimeBytecode);
    }
}
