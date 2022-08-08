package com.hedera.mirror.web3.service.eth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

@Data
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class TxnResult {

    /**
     * The status of the transaction after being processed.
     */
    public enum Status {
        /**
         * The transaction was successfully processed.
         */
        SUCCESSFUL,

        /**
         * The transaction failed to be completely processed.
         */
        FAILED
    }

    private long gasUsed;
    private long sbhRefund;
    private long gasPrice;
    private Status status;
    private String output;
    private List<String> logs;
    private String revertReason;
    private String recipient;
    private String haltReason;
    private Map<String, Map<String, Pair<String, String>>> stateChanges;
    private List<String> createdContracts;

    public TxnResult(long gasUsed, long sbhRefund, long gasPrice,
            Status status, String output, List<String> logs, String revertReason,
            String recipient, String haltReason,
            Map<String, Map<String, Pair<String, String>>> stateChanges,
            List<String> createdContracts) {
        this.gasUsed = gasUsed;
        this.sbhRefund = sbhRefund;
        this.gasPrice = gasPrice;
        this.status = status;
        this.output = output;
        this.logs = logs;
        this.revertReason = revertReason;
        this.recipient = recipient;
        this.haltReason = haltReason;
        this.stateChanges = stateChanges;
        this.createdContracts = createdContracts;
    }
}
