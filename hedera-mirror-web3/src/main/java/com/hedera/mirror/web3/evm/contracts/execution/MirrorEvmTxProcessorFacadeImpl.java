package com.hedera.mirror.web3.evm.contracts.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Provider;

import com.hedera.mirror.web3.evm.MirrorOperationTracer;

import com.hedera.services.evm.contracts.execution.traceability.DefaultHederaTracer;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.services.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.services.evm.store.contracts.AbstractCodeCache;
import com.hedera.services.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.services.evm.store.contracts.HederaEvmWorldState;
import com.hedera.services.evm.store.models.HederaEvmAccount;

@Named
public class MirrorEvmTxProcessorFacadeImpl implements MirrorEvmTxProcessorFacade {
    // Beans
    private final MirrorEntityAccess entityAccess;
    private final MirrorNodeEvmProperties evmProperties;
    private final StaticBlockMetaSource blockMetaSource;
    private final MirrorEvmContractAliases aliasManager;
    private final PricesAndFeesImpl pricesAndFees;
    // POJO
    private AbstractCodeCache codeCache;
    private HederaEvmMutableWorldState worldState;
    //HARD CODED
    private GasCalculator gasCalculator;
    private Map<String, Provider<MessageCallProcessor>> mcps;
    private Map<String, Provider<ContractCreationProcessor>> ccps;

    //desired class
    private MirrorEvmTxProcessor processor;

    public MirrorEvmTxProcessorFacadeImpl(MirrorEntityAccess entityAccess, MirrorNodeEvmProperties evmProperties,
                                          StaticBlockMetaSource blockMetaSource,
                                          MirrorEvmContractAliases aliasManager, PricesAndFeesImpl pricesAndFees,
                                          MirrorOperationTracer tracer) {
        this.aliasManager = aliasManager;
        //needed for HederaEvmWorldState
        this.entityAccess = entityAccess;
        this.evmProperties = evmProperties;
        this.codeCache = new AbstractCodeCache(1, entityAccess);
        this.blockMetaSource = blockMetaSource;
        this.pricesAndFees = pricesAndFees;
        this.worldState = new HederaEvmWorldState(entityAccess, evmProperties, codeCache);
        //
        this.gasCalculator = new BerlinGasCalculator();
        constructDummyPrecompileMaps();

        processor = new MirrorEvmTxProcessor(worldState, pricesAndFees,
                evmProperties, gasCalculator, mcps, ccps,
                blockMetaSource, aliasManager, entityAccess);
        processor.setOperationTracer(new DefaultHederaTracer());
    }

    @Override
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData) {

        return processor.execute(sender, receiver,
                providedGasLimit, value, callData,
                Instant.now(), true);
    }

    private void constructDummyPrecompileMaps() {
        String EVM_VERSION_0_30 = "v0.30";
        String EVM_VERSION_0_32 = "v0.32";
        var evm = new EVM(new OperationRegistry(), gasCalculator, EvmConfiguration.DEFAULT);
        this.mcps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> new MessageCallProcessor(
                                evm, new PrecompileContractRegistry()),
                        EVM_VERSION_0_32,
                        () -> new MessageCallProcessor(
                                evm, new PrecompileContractRegistry()));
        this.ccps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> new ContractCreationProcessor(
                                gasCalculator, evm, true, List.of(), 1),
                        EVM_VERSION_0_32,
                        () -> new ContractCreationProcessor(
                                gasCalculator, evm, true, List.of(), 1));
    }

//    final HederaEvmMutableWorldState worldState, [x]
//    final PricesAndFeesProvider pricesAndFeesProvider,[N]
//    final EvmProperties dynamicProperties,[x]
//    final GasCalculator gasCalculator, BerlinGasCalculator using for now [x]
//    final Map<String, Provider<MessageCallProcessor>> mcps,[N]
//    final Map<String, Provider<ContractCreationProcessor>> ccps, [N]
//    final BlockMetaSource blockMetaSource,[x]
//    final HederaEvmContractAliases aliasManager,[x]
//    final HederaEvmEntityAccess evmEntityAccess [x]
}
