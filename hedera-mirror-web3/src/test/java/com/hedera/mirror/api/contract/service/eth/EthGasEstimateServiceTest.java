package com.hedera.mirror.api.contract.service.eth;

import static com.hedera.mirror.web3.service.eth.EthGasEstimateService.METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.evm.SimulatedGasCalculator;
import com.hedera.mirror.web3.evm.SimulatedPricesSource;
import com.hedera.mirror.web3.evm.SimulatedStackedWorldStateUpdater;
import com.hedera.mirror.web3.evm.SimulatedWorldState;
import com.hedera.mirror.web3.evm.SimulatedWorldState.Updater;
import com.hedera.mirror.web3.evm.properties.BlockMetaSourceProvider;
import com.hedera.mirror.web3.evm.properties.EvmProperties;
import com.hedera.mirror.web3.evm.properties.SimulatedBlockMetaSource;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.service.eth.EthGasEstimateService;
import com.hedera.mirror.web3.service.eth.EthParams;
import com.hedera.mirror.web3.service.eth.TxnCallBody;
import com.hedera.services.transaction.models.Id;

@ExtendWith(MockitoExtension.class)
class EthGasEstimateServiceTest {

    @Mock private EntityRepository entityRepository;
    @Mock private EvmProperties evmProperties;
    @Mock private SimulatedGasCalculator gasCalculator;
    @Mock private BlockMetaSourceProvider blockMetaSourceProvider;
    @Mock private Entity senderEntity;
    @Mock private SimulatedPricesSource simulatedPricesSource;
    @Mock private SimulatedWorldState hederaWorldState;
    @Mock private Updater updater;
    @Mock private SimulatedStackedWorldStateUpdater simulatedStackedWorldStateUpdater;
    @Mock private EvmAccount senderAccount;
    @Mock private EvmAccount recipientAccount;
    @Mock private MutableAccount mutableSender;
    @Mock private MutableAccount mutableRecipient;

    private Address senderAddress = new Id(0,0,1250).asEvmAddress();

    @InjectMocks private EthGasEstimateService ethGasEstimateService;

    @Test
    void ethGasEstimateForTransferHbarsWorks() {
        when(hederaWorldState.updater()).thenReturn(updater);
        when(updater.updater()).thenReturn(simulatedStackedWorldStateUpdater);
        when(updater.getOrCreateSenderAccount(senderAddress)).thenReturn(senderAccount);
        when(senderAccount.getMutable()).thenReturn(mutableSender);
        when(mutableSender.getBalance()).thenReturn(Wei.of(1000L));
        when(simulatedStackedWorldStateUpdater.getSenderAccount(any())).thenReturn(senderAccount);
        when(simulatedStackedWorldStateUpdater.getOrCreate(any())).thenReturn(recipientAccount);
        when(recipientAccount.getMutable()).thenReturn(mutableRecipient);
        when(evmProperties.getChainId()).thenReturn(298);
        when(entityRepository.findAccountByAddress(
                        Bytes.fromHexString("0x00000000000000000000000000000000000004e2")
                                .toArray()))
                .thenReturn(Optional.of(senderEntity));
        when(blockMetaSourceProvider.computeBlockValues(100L))
                .thenReturn(new SimulatedBlockMetaSource(100L, 1, Instant.now().getEpochSecond()));

        when(senderEntity.getAlias())
                .thenReturn(
                        Bytes.fromHexString(
                                        "0x3a21034634b3df5289f084dd0bbbfa679a5e201d67bce7fb03fa90c3fe3210f916d433")
                                .toArray());
        when(senderEntity.getNum()).thenReturn(1250L);

        when(gasCalculator.getMaxRefundQuotient()).thenReturn(2L);
        when(simulatedPricesSource.currentGasPrice(any(), any())).thenReturn(1L);

        final var ethCallParams =
                new EthParams(
                       "0x00000000000000000000000000000000000004e2",
                        "0x00000000000000000000000000000000000004e3",
                        "100",
                        "1",
                        "1",
                        "0x");

        final var transactionCall = new TxnCallBody(ethCallParams, "latest");
        final var result = ethGasEstimateService.get(transactionCall);
        Assertions.assertEquals(Bytes.wrap(String.valueOf(100L).getBytes()).toHexString(), result);
    }

    @Test
    void getMethod() {
        assertThat(ethGasEstimateService.getMethod()).isEqualTo(METHOD);
    }
}
