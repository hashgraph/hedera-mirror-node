package com.hedera.mirror.web3.service.eth;

import static com.hedera.mirror.web3.service.eth.EthCallService.ETH_CALL_METHOD;
import static com.hedera.mirror.web3.service.eth.EthGasEstimateService.ETH_GAS_ESTIMATE_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.hedera.mirror.web3.evm.CodeCache;

import com.hedera.mirror.web3.evm.SimulatedAliasManager;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
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
public class EthCallServiceTest {
    @Mock
    private EntityRepository entityRepository;
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

    private final Address senderAddress = new Id(0,0,1250).asEvmAddress();
    private final Bytes runtimeByteCodeBytes =
            Bytes.fromHexString("0x60806040526004361061004a5760003560e01c806350e587861461004f5780636f0fccab1461007a5780638070450f146100b757806380b9f03c146100e257806381a73ad5146100fe575b600080fd5b34801561005b57600080fd5b5061006461013b565b60405161007191906103b3565b60405180910390f35b34801561008657600080fd5b506100a1600480360381019061009c9190610447565b6101cd565b6040516100ae91906103b3565b60405180910390f35b3480156100c357600080fd5b506100cc61024a565b6040516100d9919061048d565b60405180910390f35b6100fc60048036038101906100f791906104e6565b610253565b005b34801561010a57600080fd5b5061012560048036038101906101209190610447565b61029d565b60405161013291906103b3565b60405180910390f35b60606000805461014a90610542565b80601f016020809104026020016040519081016040528092919081815260200182805461017690610542565b80156101c35780601f10610198576101008083540402835291602001916101c3565b820191906000526020600020905b8154815290600101906020018083116101a657829003601f168201915b5050505050905090565b60608173ffffffffffffffffffffffffffffffffffffffff166306fdde036040518163ffffffff1660e01b8152600401600060405180830381865afa15801561021a573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906102439190610699565b9050919050565b60006004905090565b8073ffffffffffffffffffffffffffffffffffffffff166108fc349081150290604051600060405180830381858888f19350505050158015610299573d6000803e3d6000fd5b5050565b60608173ffffffffffffffffffffffffffffffffffffffff166395d89b416040518163ffffffff1660e01b8152600401600060405180830381865afa1580156102ea573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906103139190610699565b9050919050565b600081519050919050565b600082825260208201905092915050565b60005b83811015610354578082015181840152602081019050610339565b83811115610363576000848401525b50505050565b6000601f19601f8301169050919050565b60006103858261031a565b61038f8185610325565b935061039f818560208601610336565b6103a881610369565b840191505092915050565b600060208201905081810360008301526103cd818461037a565b905092915050565b6000604051905090565b600080fd5b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000610414826103e9565b9050919050565b61042481610409565b811461042f57600080fd5b50565b6000813590506104418161041b565b92915050565b60006020828403121561045d5761045c6103df565b5b600061046b84828501610432565b91505092915050565b6000819050919050565b61048781610474565b82525050565b60006020820190506104a2600083018461047e565b92915050565b60006104b3826103e9565b9050919050565b6104c3816104a8565b81146104ce57600080fd5b50565b6000813590506104e0816104ba565b92915050565b6000602082840312156104fc576104fb6103df565b5b600061050a848285016104d1565b91505092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b6000600282049050600182168061055a57607f821691505b60208210810361056d5761056c610513565b5b50919050565b600080fd5b600080fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b6105b582610369565b810181811067ffffffffffffffff821117156105d4576105d361057d565b5b80604052505050565b60006105e76103d5565b90506105f382826105ac565b919050565b600067ffffffffffffffff8211156106135761061261057d565b5b61061c82610369565b9050602081019050919050565b600061063c610637846105f8565b6105dd565b90508281526020810184848401111561065857610657610578565b5b610663848285610336565b509392505050565b600082601f8301126106805761067f610573565b5b8151610690848260208601610629565b91505092915050565b6000602082840312156106af576106ae6103df565b5b600082015167ffffffffffffffff8111156106cd576106cc6103e4565b5b6106d98482850161066b565b9150509291505056fea26469706673582212208f4200ce265190a516b326dc723f9e4430f4d3ffb4b6ed4d36ae7f0627812b4364736f6c634300080f0033");
    private final Code runtimeCode =
            Code.createLegacyCode(runtimeByteCodeBytes, Hash.hash(runtimeByteCodeBytes));

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
        when(evmProperties.getChainId()).thenReturn(298);
        when(entityRepository.findAccountByAddress(
                Bytes.fromHexString("0x00000000000000000000000000000000000004e2")
                        .toArray()))
                .thenReturn(Optional.of(senderEntity));
        when(blockMetaSourceProvider.computeBlockValues(30400L))
                .thenReturn(new SimulatedBlockMetaSource(30400L, 1, Instant.now().getEpochSecond()));

        when(senderEntity.getAlias())
                .thenReturn(
                        Bytes.fromHexString(
                                        "0x3a21034634b3df5289f084dd0bbbfa679a5e201d67bce7fb03fa90c3fe3210f916d433")
                                .toArray());
        when(senderEntity.getNum()).thenReturn(1250L);

        when(gasCalculator.getMaxRefundQuotient()).thenReturn(2L);
        when(simulatedPricesSource.currentGasPrice(any(), any())).thenReturn(1L);
        when(codeCache.getIfPresent(any())).thenReturn(Code.EMPTY);

        final var ethCallParams =
                new EthParams(
                        "0x00000000000000000000000000000000000004e2",
                        "0x00000000000000000000000000000000000004e3",
                        "0x76c0",
                        "0x76c0",
                        "0x76c0",
                        "");

        final var transactionCall = new TxnCallBody(ethCallParams, "latest");
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
        when(evmProperties.getChainId()).thenReturn(298);
        when(entityRepository.findAccountByAddress(
                Bytes.fromHexString("0x00000000000000000000000000000000000004e2")
                        .toArray()))
                .thenReturn(Optional.of(senderEntity));
        when(blockMetaSourceProvider.computeBlockValues(30400L))
                .thenReturn(new SimulatedBlockMetaSource(30400L, 1, Instant.now().getEpochSecond()));

        when(senderEntity.getAlias())
                .thenReturn(
                        Bytes.fromHexString(
                                        "0x3a21034634b3df5289f084dd0bbbfa679a5e201d67bce7fb03fa90c3fe3210f916d433")
                                .toArray());
        when(senderEntity.getNum()).thenReturn(1250L);

        when(gasCalculator.getMaxRefundQuotient()).thenReturn(2L);
        when(simulatedPricesSource.currentGasPrice(any(), any())).thenReturn(1L);
        when(codeCache.getIfPresent(any())).thenReturn(runtimeCode);

        final var ethCallParams =
                new EthParams(
                        "0x00000000000000000000000000000000000004e2",
                        "0x00000000000000000000000000000000000004e4",
                        "0x76c0",
                        "0x76c0",
                        "0",
                        "0x8070450f");

        final var transactionCall = new TxnCallBody(ethCallParams, "latest");
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
