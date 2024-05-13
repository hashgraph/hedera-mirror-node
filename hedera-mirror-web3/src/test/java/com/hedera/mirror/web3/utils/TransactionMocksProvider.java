package com.hedera.mirror.web3.utils;

import com.hedera.mirror.common.domain.TransactionMocks;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class TransactionMocksProvider implements ArgumentsProvider {

    private final TransactionMocks transactionMocks = new TransactionMocks();

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Stream.of(
                Arguments.of(transactionMocks.createContractTx, transactionMocks.createContractEthTx, transactionMocks.createContractRecordFile),
                Arguments.of(transactionMocks.contractCallTx, transactionMocks.contractCallEthTx, transactionMocks.contractCallRecordFile),
                Arguments.of(transactionMocks.eip1559Tx, transactionMocks.eip1559EthTx, transactionMocks.eip1559RecordFile),
                Arguments.of(transactionMocks.eip2930Tx, transactionMocks.eip2930EthTx, transactionMocks.eip2930RecordFile)
        );
    }
}
