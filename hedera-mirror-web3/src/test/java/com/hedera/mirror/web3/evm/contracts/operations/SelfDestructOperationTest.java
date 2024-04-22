package com.hedera.mirror.web3.evm.contracts.operations;

import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.ContractCallTestSetup;
import com.hedera.mirror.web3.viewmodel.BlockType;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SelfDestructOperationTest extends ContractCallTestSetup {

    @Test
    void testSuccesfullExecute() {
        final var destroyContractInput = "0x9a0313ab000000000000000000000000" + SENDER_ALIAS.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(destroyContractInput), SELF_DESTRUCT_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    @Test
    void testExecuteWithInvalidOwner() {
        final var destroyContractInput = "0x9a0313ab000000000000000000000000" + SENDER_ADDRESS.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(destroyContractInput), SELF_DESTRUCT_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertEquals(INVALID_SOLIDITY_ADDRESS.name(),
                assertThrows(MirrorEvmTransactionException.class, () -> contractCallService.processCall(serviceParameters)).getMessage());
    }
}