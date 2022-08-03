package com.hedera.mirror.api.contract.service.eth;

import static com.hedera.mirror.api.contract.service.eth.EthGasEstimateService.METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.api.contract.evm.SimulatedEntityAccess;
import com.hedera.mirror.api.contract.evm.SimulatedPricesSource;
import com.hedera.mirror.api.contract.evm.properties.BlockMetaSourceProvider;
import com.hedera.mirror.api.contract.evm.properties.EvmProperties;
import com.hedera.mirror.api.contract.evm.properties.SimulatedBlockMetaSource;
import com.hedera.mirror.api.contract.service.eth.TxnResult.Status;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.repository.EntityRepository;

@ExtendWith(MockitoExtension.class)
class EthGasEstimateServiceTest {

    @Mock private EntityRepository entityRepository;
    @Mock private EvmProperties evmProperties;
    @Mock private LondonGasCalculator gasCalculator;
    @Mock private SimulatedEntityAccess simulatedEntityAccess;
    @Mock private BlockMetaSourceProvider blockMetaSourceProvider;
    @Mock private Entity senderEntity;
    @Mock private SimulatedPricesSource simulatedPricesSource;

    @InjectMocks private EthGasEstimateService ethGasEstimateService;

    @Test
    void ethGasEstimateForTransferHbarsWorks() {
        when(evmProperties.getChainId()).thenReturn(298);
        when(entityRepository.findAccountByAddress(
                        Bytes.fromHexString("0x00000000000000000000000000000000000004e2")
                                .toArray()))
                .thenReturn(Optional.of(senderEntity));
        when(blockMetaSourceProvider.computeBlockValues(100L))
                .thenReturn(new SimulatedBlockMetaSource(100L, 1, Instant.now().getEpochSecond()));

        final var senderAddress =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000004e2"));
        when(simulatedEntityAccess.isExtant(senderAddress)).thenReturn(true);
        when(simulatedEntityAccess.isDeleted(senderAddress)).thenReturn(false);
        when(simulatedEntityAccess.isDetached(senderAddress)).thenReturn(false);
        when(simulatedEntityAccess.getBalance(senderAddress)).thenReturn(2000L);
        when(entityRepository.findAccountNonceByAddress(senderAddress.toArray()))
                .thenReturn(Optional.of(1L));

        final var recipientAddress =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000004e3"));
        when(simulatedEntityAccess.isExtant(recipientAddress)).thenReturn(true);
        when(simulatedEntityAccess.isDeleted(recipientAddress)).thenReturn(false);
        when(simulatedEntityAccess.isDetached(recipientAddress)).thenReturn(false);
        when(simulatedEntityAccess.getBalance(recipientAddress)).thenReturn(200L);
        when(entityRepository.findAccountNonceByAddress(recipientAddress.toArray()))
                .thenReturn(Optional.of(1L));

        when(senderEntity.getAlias())
                .thenReturn(
                        Bytes.fromHexString(
                                        "15706b229b3ba33d4a5a41ff54ce1cfe0a3d308672a33ff382f81583e02bd743")
                                .toArray());
        when(senderEntity.getNum()).thenReturn(1250L);

        when(gasCalculator.getMaxRefundQuotient()).thenReturn(2L);
        when(simulatedPricesSource.currentGasPrice(any(), any())).thenReturn(1L);

        final var ethCallParams =
                new EthParams(
                        Optional.of("0x00000000000000000000000000000000000004e2"),
                        "0x00000000000000000000000000000000000004e3",
                        Optional.of(100),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("0x"));

        final var transactionCall = new TxnCallBody(ethCallParams, "latest");
        final var result = ethGasEstimateService.get(transactionCall);
        Assertions.assertEquals(Status.SUCCESSFUL.name(), result.getStatus().name());
        Assertions.assertEquals("", result.getRevertReason());
        Assertions.assertEquals("", result.getHaltReason());
        Assertions.assertEquals(new ArrayList<>(), result.getCreatedContracts());
        Assertions.assertEquals(new ArrayList<>(), result.getLogs());
        Assertions.assertEquals(new HashMap<>(), result.getStateChanges());
        Assertions.assertEquals("0x00000000000000000000000000000000000004e3", result.getRecipient());
        Assertions.assertEquals("0x", result.getOutput());
        Assertions.assertEquals(100L, result.getGasUsed());
        Assertions.assertEquals(1L, result.getGasPrice());
        Assertions.assertEquals(0L, result.getSbhRefund());
    }

    @Test
    void getMethod() {
        assertThat(ethGasEstimateService.getMethod()).isEqualTo(METHOD);
    }
}
