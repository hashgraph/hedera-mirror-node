package com.hedera.mirror.api.contract.service.eth;

import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

import com.hedera.mirror.api.contract.evm.AliasesResolver;
import com.hedera.mirror.api.contract.evm.CallEvmTxProcessor;
import com.hedera.mirror.api.contract.evm.CodeCache;
import com.hedera.mirror.api.contract.evm.SimulatedEntityAccess;
import com.hedera.mirror.api.contract.evm.SimulatedPricesSource;
import com.hedera.mirror.api.contract.evm.SimulatedUpdater;
import com.hedera.mirror.api.contract.evm.properties.BlockMetaSourceProvider;
import com.hedera.mirror.api.contract.evm.properties.EvmProperties;
import com.hedera.mirror.api.contract.service.ResultConverterUtils;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.service.eth.EthService;

@Named
@RequiredArgsConstructor
public class EthGasEstimateService implements EthService<TxnCallBody, TxnResult> {

    static final String METHOD = "eth_gasEstimate";

    private final EntityRepository entityRepository;
    private final EvmProperties evmProperties;
    private final LondonGasCalculator londonGasCalculator;
    private final SimulatedPricesSource simulatedPricesSource;
    private final ContractRepository contractRepository;
    private final AliasesResolver aliasesResolver;
    private final SimulatedEntityAccess entityAccess;
    private final CodeCache codeCache;
    private final BlockMetaSourceProvider blockMetaSourceProvider;

    private CallEvmTxProcessor evmTxProcessor;
    private SimulatedUpdater simulatedUpdater;

    @Override
    public String getMethod() {
        return METHOD;
    }

    @Override
    public TxnResult get(final TxnCallBody request) {
        final var ethCallParams = request.getEthParams();
        final var sender = ethCallParams.getFrom().orElse("");
        final var senderEvmAddress = Bytes.fromHexString(sender).toArray();
        final var receiverAddress = ethCallParams.getTo() != null ? Address.wrap(Bytes.fromHexString(ethCallParams.getTo())) : Address.ZERO;
        final var gasPrice = ethCallParams.getGasPrice().orElse(0);
        final var gasLimit = ethCallParams.getGas().orElse(0);
        final var value = ethCallParams.getValue().orElse(0);
        final var payload = ethCallParams.getData().isPresent() ? Bytes.fromHexString(ethCallParams.getData().get()) : null;

        final var senderEntity = entityRepository.findAccountByAddress(senderEvmAddress).orElse(null);
        final var senderDto = senderEntity != null ? new AccountDto(senderEntity.getNum(), ByteString.copyFrom(senderEntity.getAlias())) : new AccountDto(0L, ByteString.EMPTY);

        simulatedUpdater = new SimulatedUpdater(contractRepository, entityRepository, aliasesResolver, entityAccess, codeCache);
        evmTxProcessor = new CallEvmTxProcessor(simulatedPricesSource, evmProperties, londonGasCalculator, new HashSet<>(), new HashMap<>());
        evmTxProcessor.setWorldUpdater(simulatedUpdater);
        evmTxProcessor.setBlockMetaSource(blockMetaSourceProvider);

        final var txnProcessingResult = evmTxProcessor.executeEth(
                senderDto,
                receiverAddress,
                gasLimit,
                value,
                payload,
                Instant.now(),
                BigInteger.valueOf(0L),
                senderDto,
                20_000L,
                false
        );

        return ResultConverterUtils.fromTransactionProcessingResult(txnProcessingResult);
    }
}

