package com.hedera.mirror.web3.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.web3.service.eth.TxnResult;
import com.hedera.mirror.web3.service.eth.TxnResult.Status;
import com.hedera.services.transaction.TransactionProcessingResult;

public class ResultConverterUtils {

    public static TxnResult fromTransactionProcessingResult(final TransactionProcessingResult transactionProcessingResult) {
        final var gasUsed = transactionProcessingResult.getGasUsed();
        final var sbhRefund = transactionProcessingResult.getSbhRefund();
        final var gasPrice = transactionProcessingResult.getGasPrice();
        final var status = transactionProcessingResult.isSuccessful() ? Status.SUCCESSFUL : Status.FAILED;
        final var output = transactionProcessingResult.getOutput().toHexString();

        final var logs = new ArrayList<String>();
        for(final var log: transactionProcessingResult.getLogs()) {
            logs.add(log.toString());
        }

        final var revertReason = transactionProcessingResult.getRevertReason().isPresent() ? transactionProcessingResult.getRevertReason().get().toString() : "";
        final var recipient = transactionProcessingResult.getRecipient().isPresent() ? transactionProcessingResult.getRecipient().get().toHexString() : "";
        final var haltReason = transactionProcessingResult.getHaltReason().isPresent() ? transactionProcessingResult.getHaltReason().get().getDescription() : "";
        final var stateChanges = new HashMap<String, Map<String, Pair<String, String>>>();
        final var createdContracts = new ArrayList<String>();

        return new TxnResult(gasUsed, sbhRefund, gasPrice, status, output, logs, revertReason, recipient, haltReason, stateChanges, createdContracts);
    }
}
