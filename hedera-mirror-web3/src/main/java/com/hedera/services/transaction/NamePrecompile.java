package com.hedera.services.transaction;

import com.hedera.mirror.web3.repository.TokenRepository;

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class NamePrecompile implements Precompile {
    protected byte[] address;
    protected TokenRepository tokenRepository;
    protected EncodingFacade encoder;

    public NamePrecompile(byte[] address) {
        this.address = address;
    }

    @Override
    public Builder body(
            Bytes input, UnaryOperator<byte[]> aliasResolver) {
        return null;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return 0;
    }

    @Override
    public Bytes getSuccessResultFor() {
        // FIX
        final var token = tokenRepository.findByAddress(address);
        return encoder.encodeName(token.get().getName());
    }
}
