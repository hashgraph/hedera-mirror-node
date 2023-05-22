package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.services.store.contracts.precompile.Precompile;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Set;
import java.util.function.UnaryOperator;

import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;

public class MockPrecompile implements Precompile {

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        return TransactionBody.newBuilder();
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return 0;
    }

    @Override
    public void run(MessageFrame frame) {

    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return 0;
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(0x00000000);
    }

    @Override
    public Bytes getSuccessResultFor() {
        return SUCCESS_RESULT;
    }
}
