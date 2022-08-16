package com.hedera.mirror.web3.service.eth;

import static com.hedera.mirror.web3.service.eth.Constants.HTS_PRECOMPILED_CONTRACT_ADDRESS;

import com.google.protobuf.ByteString;

import com.hedera.mirror.web3.repository.TokenRepository;

import com.hedera.services.transaction.HTSPrecompiledContract;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.inject.Named;

import com.hedera.mirror.web3.evm.CodeCache;

import com.hedera.mirror.web3.evm.SimulatedAliasManager;

import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.web3.evm.CallEvmTxProcessor;
import com.hedera.mirror.web3.evm.SimulatedGasCalculator;
import com.hedera.mirror.web3.evm.SimulatedPricesSource;
import com.hedera.mirror.web3.evm.SimulatedWorldState;
import com.hedera.mirror.web3.evm.properties.BlockMetaSourceProvider;
import com.hedera.mirror.web3.evm.properties.EvmProperties;
import com.hedera.mirror.web3.repository.EntityRepository;

import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Named
@RequiredArgsConstructor
public class EthGasEstimateService implements ApiContractEthService<TxnCallBody, String> {

    public static final String ETH_GAS_ESTIMATE_METHOD = "eth_gasEstimate";

    private final EntityRepository entityRepository;
    private final EvmProperties evmProperties;
    private final SimulatedGasCalculator simulatedGasCalculator;
    private final SimulatedPricesSource simulatedPricesSource;
    private final BlockMetaSourceProvider blockMetaSourceProvider;
    private final SimulatedWorldState worldState;
    private final CodeCache codeCache;
    private final SimulatedAliasManager simulatedAliasManager;
    private final TokenRepository tokenRepository;

    @Override
    public String getMethod() {
        return ETH_GAS_ESTIMATE_METHOD;
    }

    @Override
    public String get(final TxnCallBody request) {
        final var ethCallParams = request.getEthParams();
        final var sender = ethCallParams.getFrom();
        final var senderEvmAddress = Bytes.fromHexString(sender).toArray();
        final var receiverAddress = ethCallParams.getTo() != null ? Address.wrap(Bytes.fromHexString(ethCallParams.getTo())) : Address.ZERO;
        final var gasPrice = Integer.decode(ethCallParams.getGasPrice());
        final var gasLimit = Integer.decode(ethCallParams.getGas());
        final var value = Long.decode(ethCallParams.getValue());
        final var payload = Bytes.fromHexString(ethCallParams.getData());

        final var senderEntity = entityRepository.findAccountByAddress(senderEvmAddress).orElse(null);
        final var senderDto = senderEntity != null ? new AccountDto(senderEntity.getNum(), ByteString.copyFrom(senderEntity.getAlias())) : new AccountDto(0L, ByteString.EMPTY);

        Map<String, PrecompiledContract> precompiledContractMap = new HashMap<>();
        precompiledContractMap.put(HTS_PRECOMPILED_CONTRACT_ADDRESS, new HTSPrecompiledContract(tokenRepository));

        final CallEvmTxProcessor evmTxProcessor = new CallEvmTxProcessor(simulatedPricesSource, evmProperties,
                simulatedGasCalculator, new HashSet<>(), precompiledContractMap, codeCache, simulatedAliasManager);
        evmTxProcessor.setWorldState(worldState);
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
                0L,
                false
        );

        return Bytes.wrap(String.valueOf(txnProcessingResult.getGasUsed()).getBytes()).toHexString();
    }
}

