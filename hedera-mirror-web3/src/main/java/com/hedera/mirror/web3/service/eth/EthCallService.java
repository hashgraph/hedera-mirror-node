package com.hedera.mirror.web3.service.eth;

import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
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

@Named
@RequiredArgsConstructor
public class EthCallService implements ApiContractEthService<TxnCallBody, String> {

    static final String ETH_CALL_METHOD = "eth_call";

    private final EntityRepository entityRepository;
    private final EvmProperties evmProperties;
    private final SimulatedGasCalculator simulatedGasCalculator;
    private final SimulatedPricesSource simulatedPricesSource;
    private final BlockMetaSourceProvider blockMetaSourceProvider;
    private final SimulatedWorldState worldState;
    private final CodeCache codeCache;
    private final SimulatedAliasManager simulatedAliasManager;

    @Override
    public String getMethod() {
        return ETH_CALL_METHOD;
    }

    @Override
    public String get(final TxnCallBody request) {
        final var ethCallParams = request.getEthParams();
        final var sender = ethCallParams.getFrom();
        final var senderEvmAddress = Bytes.fromHexString(sender).toArray();
        final var receiverAddress = ethCallParams.getTo() != null ? Address.wrap(Bytes.fromHexString(ethCallParams.getTo())) : Address.ZERO;
        final var gasLimit = Integer.decode(ethCallParams.getGas());
        final var value = Long.decode(ethCallParams.getValue());
        final var payload = Bytes.fromHexString(ethCallParams.getData());

        final var senderEntity = entityRepository.findAccountByAddress(senderEvmAddress).orElse(null);
        final var senderDto = senderEntity != null ? new AccountDto(senderEntity.getNum(), ByteString.copyFrom(senderEntity.getAlias())) : new AccountDto(0L, ByteString.EMPTY);

        final CallEvmTxProcessor evmTxProcessor = new CallEvmTxProcessor(simulatedPricesSource, evmProperties,
                simulatedGasCalculator, new HashSet<>(), new HashMap<>(), codeCache, simulatedAliasManager);
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
                true
        );

        return txnProcessingResult.getOutput().toHexString();
    }
}
