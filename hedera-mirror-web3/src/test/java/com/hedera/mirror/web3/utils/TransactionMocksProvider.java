package com.hedera.mirror.web3.utils;

import com.hedera.mirror.common.domain.TransactionMocks;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class TransactionMocksProvider implements ArgumentsProvider {

    private final TransactionMocks mocks = new TransactionMocks();

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Stream.of(
                Arguments.of(mocks.getCreateContractTx(), mocks.getCreateContractEthTx(), mocks.getCreateContractRecordFile()),
                Arguments.of(mocks.getContractCallTx(), mocks.getContractCallEthTx(), mocks.getContractCallRecordFile()),
                Arguments.of(mocks.getEip1559Tx(), mocks.getEip1559EthTx(), mocks.getEip1559RecordFile()),
                Arguments.of(mocks.getEip2930Tx(), mocks.getEip2930EthTx(), mocks.getEip2930RecordFile())
        );
    }
}
