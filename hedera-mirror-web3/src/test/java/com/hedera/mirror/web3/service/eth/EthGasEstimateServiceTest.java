package com.hedera.mirror.web3.service.eth;

import static com.hedera.mirror.web3.service.eth.EthGasEstimateService.ETH_GAS_ESTIMATE_METHOD;
import static com.hedera.mirror.web3.utils.TestConstants.blockNumber;
import static com.hedera.mirror.web3.utils.TestConstants.chainId;
import static com.hedera.mirror.web3.utils.TestConstants.contractAddress;
import static com.hedera.mirror.web3.utils.TestConstants.contractDelegateCallAddress;
import static com.hedera.mirror.web3.utils.TestConstants.contractHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.contractWithDelegateCallHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.defaultBalance;
import static com.hedera.mirror.web3.utils.TestConstants.delegateCallRuntimeCode;
import static com.hedera.mirror.web3.utils.TestConstants.gasHexValue;
import static com.hedera.mirror.web3.utils.TestConstants.gasHexValueDelegateCall;
import static com.hedera.mirror.web3.utils.TestConstants.gasLimit;
import static com.hedera.mirror.web3.utils.TestConstants.gasLimitDelegateCall;
import static com.hedera.mirror.web3.utils.TestConstants.gasPriceHexValue;
import static com.hedera.mirror.web3.utils.TestConstants.gasPriceHexValueDelegateCall;
import static com.hedera.mirror.web3.utils.TestConstants.gasUsed;
import static com.hedera.mirror.web3.utils.TestConstants.gasUsedDelegateCall;
import static com.hedera.mirror.web3.utils.TestConstants.latestTag;
import static com.hedera.mirror.web3.utils.TestConstants.receiverEvmAddress;
import static com.hedera.mirror.web3.utils.TestConstants.receiverHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.runtimeByteCodeBytes;
import static com.hedera.mirror.web3.utils.TestConstants.runtimeCode;
import static com.hedera.mirror.web3.utils.TestConstants.senderAddress;
import static com.hedera.mirror.web3.utils.TestConstants.senderAlias;
import static com.hedera.mirror.web3.utils.TestConstants.senderBalance;
import static com.hedera.mirror.web3.utils.TestConstants.senderEvmAddress;
import static com.hedera.mirror.web3.utils.TestConstants.senderHexAddress;
import static com.hedera.mirror.web3.utils.TestConstants.senderNum;
import static com.hedera.mirror.web3.utils.TestConstants.storageValue;
import static com.hedera.mirror.web3.utils.TestConstants.transferHbarsToReceiverInputData;
import static com.hedera.mirror.web3.utils.TestConstants.valueHexValue;
import static com.hedera.mirror.web3.utils.TestConstants.valueHexValueDelegateCall;
import static com.hedera.mirror.web3.utils.TestConstants.writeToStorageSlotDelegateCallInputData;
import static com.hedera.mirror.web3.utils.TestConstants.writeToStorageSlotInputData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.MutableWorldView;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
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
    @Mock private EvmAccount delegateTargetAccount;
    @Mock private MutableAccount mutableSender;
    @Mock private MutableAccount mutableRecipient;
    @Mock private MutableAccount mutableDelegate;
    @Mock private SimulatedAliasManager simulatedAliasManager;
    @Mock private MessageFrame frame;
    @Mock private CodeCache codeCache;
    @InjectMocks private EthGasEstimateService ethGasEstimateService;

    @Test
    void ethGasEstimateForTransferHbarsWorks() {
        when(hederaWorldState.updater()).thenReturn(updater);
        when(updater.updater()).thenReturn(simulatedStackedWorldStateUpdater);
        when(updater.getOrCreateSenderAccount(senderAddress)).thenReturn(senderAccount);
        when(senderAccount.getMutable()).thenReturn(mutableSender);
        when(mutableSender.getBalance()).thenReturn(Wei.of(senderBalance));
        when(simulatedStackedWorldStateUpdater.getSenderAccount(any())).thenReturn(senderAccount);
        when(simulatedStackedWorldStateUpdater.getOrCreate(any())).thenReturn(recipientAccount);
        when(recipientAccount.getMutable()).thenReturn(mutableRecipient);
        when(evmProperties.getChainId()).thenReturn(chainId);
        when(entityRepository.findAccountByAddress(receiverEvmAddress))
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
        final var result = ethGasEstimateService.get(transactionCall);
        Assertions.assertEquals(Bytes.wrap(String.valueOf(gasUsed).getBytes()).toHexString(), result);
    }

    @Test
    void ethGasEstimateForTransferHbarsInSmartContractWorks() {
        when(hederaWorldState.updater()).thenReturn(updater);
        when(updater.updater()).thenReturn(simulatedStackedWorldStateUpdater);
        when(updater.getOrCreateSenderAccount(senderAddress)).thenReturn(senderAccount);
        when(senderAccount.getMutable()).thenReturn(mutableSender);
        when(mutableSender.getBalance()).thenReturn(Wei.of(senderBalance));
        when(simulatedStackedWorldStateUpdater.getSenderAccount(any())).thenReturn(senderAccount);
        when(simulatedStackedWorldStateUpdater.getOrCreate(any())).thenReturn(recipientAccount);
        when(recipientAccount.getMutable()).thenReturn(mutableRecipient);
        when(evmProperties.getChainId()).thenReturn(chainId);
        when(entityRepository.findAccountByAddress(senderEvmAddress))
                .thenReturn(Optional.of(senderEntity));
        when(blockMetaSourceProvider.computeBlockValues(gasLimit))
                .thenReturn(new SimulatedBlockMetaSource(gasLimit, blockNumber, Instant.now().getEpochSecond()));

        when(senderEntity.getAlias())
                .thenReturn(senderAlias);
        when(senderEntity.getNum()).thenReturn(senderNum);

        when(gasCalculator.getMaxRefundQuotient()).thenReturn(2L);
        when(simulatedPricesSource.currentGasPrice(any(), any())).thenReturn(1L);

        when(codeCache.getIfPresent(any())).thenReturn(runtimeCode);

        final var ethCallParams =
                new EthParams(
                        senderHexAddress,
                        contractHexAddress,
                        gasHexValue,
                        gasPriceHexValue,
                        valueHexValue,
                        transferHbarsToReceiverInputData);

        final var transactionCall = new TxnCallBody(ethCallParams, latestTag);
        final var result = ethGasEstimateService.get(transactionCall);
        Assertions.assertEquals(Bytes.wrap(String.valueOf(gasUsed).getBytes()).toHexString(), result);
    }

    @Test
    void ethGasEstimateForWritingToContractStorageWorks() {
        when(hederaWorldState.updater()).thenReturn(updater);
        when(updater.updater()).thenReturn(simulatedStackedWorldStateUpdater);
        when(updater.getOrCreateSenderAccount(senderAddress)).thenReturn(senderAccount);
        when(senderAccount.getMutable()).thenReturn(mutableSender);
        when(simulatedStackedWorldStateUpdater.getSenderAccount(any())).thenReturn(senderAccount);
        when(simulatedStackedWorldStateUpdater.get(contractAddress)).thenReturn(recipientAccount);
        when(simulatedStackedWorldStateUpdater.getOrCreate(any())).thenReturn(recipientAccount);
        when(simulatedStackedWorldStateUpdater.getAccount(contractAddress)).thenReturn(recipientAccount);
        when(recipientAccount.getAddress()).thenReturn(contractAddress);
        when(recipientAccount.getMutable()).thenReturn(mutableRecipient);
        when(evmProperties.getChainId()).thenReturn(chainId);
        when(entityRepository.findAccountByAddress(senderEvmAddress)).thenReturn(Optional.of(senderEntity));
        when(mutableSender.getBalance()).thenReturn(Wei.of(senderBalance));
        when(recipientAccount.getStorageValue(UInt256.valueOf(1))).thenReturn(UInt256.fromHexString(storageValue));

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
                        contractHexAddress,
                        gasHexValue,
                        gasPriceHexValue,
                        valueHexValue,
                        writeToStorageSlotInputData);

        final var transactionCall = new TxnCallBody(ethCallParams, latestTag);
        final var result = ethGasEstimateService.get(transactionCall);
        Assertions.assertEquals(Bytes.wrap(String.valueOf(gasUsed).getBytes()).toHexString(), result);
    }

    @Test
    void ethGasEstimateForWritingToContractStorageWithDelegateCallWorks() {
        when(hederaWorldState.updater()).thenReturn(updater);
        when(updater.updater()).thenReturn(simulatedStackedWorldStateUpdater);
        when(simulatedStackedWorldStateUpdater.updater()).thenReturn(simulatedStackedWorldStateUpdater);
        when(simulatedStackedWorldStateUpdater.get(contractDelegateCallAddress)).thenReturn(delegateTargetAccount);
        when(simulatedStackedWorldStateUpdater.getAccount(contractDelegateCallAddress)).thenReturn(delegateTargetAccount);
        when(delegateTargetAccount.getMutable()).thenReturn(mutableDelegate);
        when(delegateTargetAccount.getAddress()).thenReturn(contractDelegateCallAddress);
        when(updater.getOrCreateSenderAccount(senderAddress)).thenReturn(senderAccount);
        when(simulatedStackedWorldStateUpdater.getSenderAccount(any())).thenReturn(senderAccount);
        when(senderAccount.getMutable()).thenReturn(mutableSender);
        when(entityRepository.findAccountByAddress(senderEvmAddress)).thenReturn(Optional.of(senderEntity));
        when(senderEntity.getAlias()).thenReturn(senderAlias);
        when(senderEntity.getNum()).thenReturn(senderNum);
        when(mutableSender.getBalance()).thenReturn(Wei.of(senderBalance));
        when(simulatedStackedWorldStateUpdater.getOrCreate(contractDelegateCallAddress)).thenReturn(delegateTargetAccount);
        when(simulatedStackedWorldStateUpdater.get(contractDelegateCallAddress)).thenReturn(delegateTargetAccount);
        when(delegateTargetAccount.getBalance()).thenReturn(Wei.of(defaultBalance));
        when(simulatedStackedWorldStateUpdater.get(contractAddress)).thenReturn(recipientAccount);
        when(evmProperties.getChainId()).thenReturn(chainId);
        when(delegateTargetAccount.getStorageValue(UInt256.valueOf(1))).thenReturn(UInt256.fromHexString(storageValue));
        when(recipientAccount.getCodeHash()).thenReturn(Hash.hash(runtimeByteCodeBytes));
        when(recipientAccount.getCode()).thenReturn(runtimeByteCodeBytes);
        when(codeCache.getIfPresent(any())).thenReturn(delegateCallRuntimeCode);

        when(blockMetaSourceProvider.computeBlockValues(gasLimitDelegateCall))
                .thenReturn(new SimulatedBlockMetaSource(gasLimitDelegateCall, blockNumber, Instant.now().getEpochSecond()));
        when(gasCalculator.getMaxRefundQuotient()).thenReturn(2L);
        when(simulatedPricesSource.currentGasPrice(any(), any())).thenReturn(1L);


        final var ethCallParams =
                new EthParams(
                        senderHexAddress,
                        contractWithDelegateCallHexAddress,
                        gasHexValueDelegateCall,
                        gasPriceHexValueDelegateCall,
                        valueHexValueDelegateCall,
                        writeToStorageSlotDelegateCallInputData);

        final var transactionCall = new TxnCallBody(ethCallParams, latestTag);
        final var result = ethGasEstimateService.get(transactionCall);
        Assertions.assertEquals(Bytes.wrap(String.valueOf(gasUsedDelegateCall).getBytes()).toHexString(), result);
    }

    @Test
    void getEthGasEstimateMethod() {
        assertThat(ethGasEstimateService.getMethod()).isEqualTo(ETH_GAS_ESTIMATE_METHOD);
    }
}
