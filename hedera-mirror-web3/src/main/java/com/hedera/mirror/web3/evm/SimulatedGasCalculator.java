package com.hedera.mirror.web3.evm;

import javax.inject.Named;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

@Named
public class SimulatedGasCalculator implements GasCalculator {

    public long transactionIntrinsicGasCost(Bytes payload, boolean isContractCreation) {
        int zeros = 0;

        int i;
        for(i = 0; i < payload.size(); ++i) {
            if (payload.get(i) == 0) {
                ++zeros;
            }
        }

        i = payload.size() - zeros;
        long cost = 21000L + 4L * (long)zeros + 16L * (long)i;
        return isContractCreation ? cost + this.txCreateExtraGasCost() : cost;
    }

    protected long txCreateExtraGasCost() {
        return 32000L;
    }

    @Override
    public long idPrecompiledContractGasCost(Bytes bytes) {
        return 0;
    }

    @Override
    public long getEcrecPrecompiledContractGasCost() {
        return 0;
    }

    @Override
    public long sha256PrecompiledContractGasCost(Bytes bytes) {
        return 0;
    }

    @Override
    public long ripemd160PrecompiledContractGasCost(Bytes bytes) {
        return 0;
    }

    @Override
    public long getZeroTierGasCost() {
        return 0;
    }

    @Override
    public long getVeryLowTierGasCost() {
        return 0;
    }

    @Override
    public long getLowTierGasCost() {
        return 0;
    }

    @Override
    public long getBaseTierGasCost() {
        return 0;
    }

    @Override
    public long getMidTierGasCost() {
        return 0;
    }

    @Override
    public long getHighTierGasCost() {
        return 0;
    }

    @Override
    public long callOperationBaseGasCost() {
        return 0;
    }

    @Override
    public long callOperationGasCost(MessageFrame messageFrame, long l,
            long l1, long l2, long l3, long l4, Wei wei,
            Account account,
            Address address) {
        return 0;
    }

    @Override
    public long getAdditionalCallStipend() {
        return 0;
    }

    @Override
    public long gasAvailableForChildCall(MessageFrame messageFrame, long l, boolean b) {
        return 0;
    }

    @Override
    public long createOperationGasCost(MessageFrame messageFrame) {
        return 0;
    }

    @Override
    public long create2OperationGasCost(MessageFrame messageFrame) {
        return 0;
    }

    @Override
    public long gasAvailableForChildCreate(long l) {
        return 0;
    }

    @Override
    public long dataCopyOperationGasCost(MessageFrame messageFrame, long l, long l1) {
        return 0;
    }

    @Override
    public long memoryExpansionGasCost(MessageFrame messageFrame, long l, long l1) {
        return 0;
    }

    @Override
    public long getBalanceOperationGasCost() {
        return 0;
    }

    @Override
    public long getBlockHashOperationGasCost() {
        return 0;
    }

    @Override
    public long expOperationGasCost(int i) {
        return 0;
    }

    @Override
    public long extCodeCopyOperationGasCost(MessageFrame messageFrame, long l, long l1) {
        return 0;
    }

    @Override
    public long extCodeHashOperationGasCost() {
        return 0;
    }

    @Override
    public long getExtCodeSizeOperationGasCost() {
        return 0;
    }

    @Override
    public long getJumpDestOperationGasCost() {
        return 0;
    }

    @Override
    public long logOperationGasCost(MessageFrame messageFrame, long l, long l1, int i) {
        return 0;
    }

    @Override
    public long mLoadOperationGasCost(MessageFrame messageFrame, long l) {
        return 0;
    }

    @Override
    public long mStoreOperationGasCost(MessageFrame messageFrame, long l) {
        return 0;
    }

    @Override
    public long mStore8OperationGasCost(MessageFrame messageFrame, long l) {
        return 0;
    }

    @Override
    public long selfDestructOperationGasCost(Account account, Wei wei) {
        return 0;
    }

    @Override
    public long keccak256OperationGasCost(MessageFrame messageFrame, long l, long l1) {
        return 0;
    }

    @Override
    public long getSloadOperationGasCost() {
        return 0;
    }

    @Override
    public long calculateStorageCost(Account account, UInt256 uInt256,
            UInt256 uInt2561) {
        return 0;
    }

    @Override
    public long calculateStorageRefundAmount(Account account,
            UInt256 uInt256, UInt256 uInt2561) {
        return 0;
    }

    public long getSelfDestructRefundAmount() {
        return 0L;
    }

    @Override
    public long codeDepositGasCost(int i) {
        return 0;
    }

    public long getMaxRefundQuotient() {
        return 5L;
    }

    @Override
    public long getMaximumTransactionCost(int i) {
        return 0;
    }
}
