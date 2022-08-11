package com.hedera.mirror.web3.service.eth;

import static com.hedera.mirror.web3.service.eth.EthCallService.ETH_CALL_METHOD;
import static com.hedera.mirror.web3.service.eth.EthGasEstimateService.ETH_GAS_ESTIMATE_METHOD;
import static com.hedera.mirror.web3.utils.TestConstants.blockNumber;
import static com.hedera.mirror.web3.utils.TestConstants.chainId;
import static com.hedera.mirror.web3.utils.TestConstants.gasHexValue;
import static com.hedera.mirror.web3.utils.TestConstants.gasLimit;
import static com.hedera.mirror.web3.utils.TestConstants.gasPriceHexValue;
import static com.hedera.mirror.web3.utils.TestConstants.latestTag;
import static com.hedera.mirror.web3.utils.TestConstants.multiplySimpleNumbersSelector;
import static com.hedera.mirror.web3.utils.TestConstants.receiverHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.runtimeCode;
import static com.hedera.mirror.web3.utils.TestConstants.senderAddress;
import static com.hedera.mirror.web3.utils.TestConstants.senderAlias;
import static com.hedera.mirror.web3.utils.TestConstants.senderEvmAddress;
import static com.hedera.mirror.web3.utils.TestConstants.senderHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.senderNum;
import static com.hedera.mirror.web3.utils.TestConstants.valueHexValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.evm.CodeCache;
import com.hedera.mirror.web3.evm.SimulatedAliasManager;
import com.hedera.mirror.web3.evm.SimulatedGasCalculator;
import com.hedera.mirror.web3.evm.SimulatedPricesSource;
import com.hedera.mirror.web3.evm.SimulatedStackedWorldStateUpdater;
import com.hedera.mirror.web3.evm.SimulatedWorldState;
import com.hedera.mirror.web3.evm.SimulatedWorldState.Updater;
import com.hedera.mirror.web3.evm.properties.BlockMetaSourceProvider;
import com.hedera.mirror.web3.evm.properties.EvmProperties;
import com.hedera.mirror.web3.evm.properties.SimulatedBlockMetaSource;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.services.transaction.models.Id;

@ExtendWith(MockitoExtension.class)
class EthCallServiceTest {
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
    @Mock private CodeCache codeCache;
    @Mock private SimulatedAliasManager simulatedAliasManager;

    @InjectMocks
    private EthGasEstimateService ethGasEstimateService;

    @InjectMocks
    private EthCallService ethCallService;

    @Test
    void ethCallWithEmptyInputWorks() {
        when(hederaWorldState.updater()).thenReturn(updater);
        when(updater.updater()).thenReturn(simulatedStackedWorldStateUpdater);
        when(updater.getOrCreateSenderAccount(senderAddress)).thenReturn(senderAccount);
        when(senderAccount.getMutable()).thenReturn(mutableSender);
        when(simulatedStackedWorldStateUpdater.getSenderAccount(any())).thenReturn(senderAccount);
        when(simulatedStackedWorldStateUpdater.getOrCreate(any())).thenReturn(recipientAccount);
        when(recipientAccount.getMutable()).thenReturn(mutableRecipient);
        when(evmProperties.getChainId()).thenReturn(chainId);
        when(entityRepository.findAccountByAddress(
                Bytes.fromHexString(senderHexAddress)
                        .toArray()))
                .thenReturn(Optional.of(senderEntity));
        when(blockMetaSourceProvider.computeBlockValues(gasLimit))
                .thenReturn(new SimulatedBlockMetaSource(gasLimit, blockNumber, Instant.now().getEpochSecond()));

        when(senderEntity.getAlias())
                .thenReturn(senderAlias);
        when(senderEntity.getNum()).thenReturn(senderNum);

        when(gasCalculator.getMaxRefundQuotient()).thenReturn(2L);
        when(simulatedPricesSource.currentGasPrice(any(), any())).thenReturn(1L);
        when(codeCache.getIfPresent(any())).thenReturn(Code.EMPTY);
        final var ethCallParams =
                new EthParams(
                        senderHexAddress,
                        receiverHexAddress,
                        gasHexValue,
                        gasPriceHexValue,
                        valueHexValue,
                        "");

        final var transactionCall = new TxnCallBody(ethCallParams, latestTag);
        final var result = ethCallService.get(transactionCall);
        Assertions.assertEquals(Bytes.EMPTY.toHexString(), result);
    }

    @Test
    void ethCallForPureFunction() {
        when(hederaWorldState.updater()).thenReturn(updater);
        when(updater.updater()).thenReturn(simulatedStackedWorldStateUpdater);
        when(updater.getOrCreateSenderAccount(senderAddress)).thenReturn(senderAccount);
        when(senderAccount.getMutable()).thenReturn(mutableSender);
        when(simulatedStackedWorldStateUpdater.getSenderAccount(any())).thenReturn(senderAccount);
        when(simulatedStackedWorldStateUpdater.getOrCreate(any())).thenReturn(recipientAccount);
        when(evmProperties.getChainId()).thenReturn(chainId);
        when(entityRepository.findAccountByAddress(senderEvmAddress))
                .thenReturn(Optional.of(senderEntity));
        when(blockMetaSourceProvider.computeBlockValues(gasLimit))
                .thenReturn(new SimulatedBlockMetaSource(gasLimit, blockNumber, Instant.now().getEpochSecond()));

        when(senderEntity.getAlias()).thenReturn(senderAlias);
        when(senderEntity.getNum()).thenReturn(senderNum);

        when(gasCalculator.getMaxRefundQuotient()).thenReturn(2L);
        when(simulatedPricesSource.currentGasPrice(any(), any())).thenReturn(1L);
        when(codeCache.getIfPresent(any())).thenReturn(runtimeCode);

        final var ethCallParams =
                new EthParams(
                        senderHexAddress,
                        receiverHexAddress,
                        gasHexValue,
                        gasPriceHexValue,
                        "0",
                        multiplySimpleNumbersSelector);

        final var transactionCall = new TxnCallBody(ethCallParams, latestTag);
        final var result = ethCallService.get(transactionCall);
        Assertions.assertEquals(4, Integer.decode(result));
    }

    @Test
    void getEthGasEstimateMethod() {
        assertThat(ethGasEstimateService.getMethod()).isEqualTo(ETH_GAS_ESTIMATE_METHOD);
    }

    @Test
    void getEthCallMethod() {
        assertThat(ethCallService.getMethod()).isEqualTo(ETH_CALL_METHOD);
    }
}
