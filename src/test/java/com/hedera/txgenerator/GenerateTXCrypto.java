package com.hedera.txgenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hedera.hashgraph.sdk.CallParams;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.account.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.account.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.account.CryptoTransferTransaction;
import com.hedera.hashgraph.sdk.contract.ContractCreateTransaction;
import com.hedera.hashgraph.sdk.contract.ContractDeleteTransaction;
import com.hedera.hashgraph.sdk.contract.ContractExecuteTransaction;
import com.hedera.hashgraph.sdk.contract.ContractId;
import com.hedera.hashgraph.sdk.contract.ContractUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.file.FileAppendTransaction;
import com.hedera.hashgraph.sdk.file.FileCreateTransaction;
import com.hedera.hashgraph.sdk.file.FileDeleteTransaction;
import com.hedera.hashgraph.sdk.file.FileId;
import com.hedera.hashgraph.sdk.file.FileUpdateTransaction;
import com.hedera.utilities.ExampleHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class GenerateTXCrypto {
    private GenerateTXCrypto() { }

    /**
     * Generates sample transactions for the purpose of creating record files
     * which contain at least one of each transaction type
     * .env file or environment variables are necessary to run this
     * The output (log.info) records the result of accounts, files and contracts that have been created,
     * as well as the inputs to each of the transaction calls
     * NODE_ID=0.0.3
	 * NODE_ADDRESS=0.testnet.hedera.com:50211
     * OPERATOR_ID=0.0.2
     * OPERATOR_KEY=
     * @param args
     * @throws HederaException
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(String[] args) throws HederaException, InterruptedException, IOException {
        // Generate a Ed25519 private, public key pair
        Ed25519PrivateKey newKey = Ed25519PrivateKey.generate();
        Ed25519PublicKey newPublicKey = newKey.getPublicKey();
        
        long oneDayOfSeconds = 60 * 60 * 24;

        logTitle("Keys");
        log.info("private key {} ", newKey);
        log.info("public key {} ", newPublicKey);

        Client client = ExampleHelper.createHederaClient();
        
        logTitle("Crypto Create with defaults");
        long initialBalance = 1000;
        log.info("Input > Initial Balance {}", initialBalance);
        long txFee = 10_000_000;
        log.info("Input > TX fee {}", txFee);
        String memo = "";
        log.info("Input > Memo {}", memo);
        long txValidDuration = 60;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        // Create a new account with minimal properties
        AccountCreateTransaction tx = new AccountCreateTransaction(client)
            // The only _required_ property here is `key`
            .setKey(newKey.getPublicKey())
            .setInitialBalance(1000)
            .setTransactionFee(txFee)
            .setTransactionValidDuration(Duration.ofSeconds(txValidDuration));

        log.info("Output > Account ID {}", tx.executeForReceipt().getAccountId());

        logTitle("Crypto Create with all parameters");
        initialBalance = 2000;
        log.info("Input > Initial Balance {}", initialBalance);
        txFee = 20_000_000;
        log.info("Input > TX fee {}", txFee);
        long autoRenewSeconds = 120 * 24 * 60 * 60; // 120 days
        log.info("Input > AutoRenew {} seconds", autoRenewSeconds );
        memo = "Account Create memo";
        log.info("Input > Memo {}", memo );
        String proxy = "0.0.3";
        log.info("Input > Proxy {}", proxy );
        long receiveThreshold = 10_000;
        log.info("Input > Receive Threshold {}", receiveThreshold );
        long sendThreshold = 15_000;
        log.info("Input > Send Threshold {}", sendThreshold );
        boolean receiveSigRequired = true;
        log.info("Input > Receiver Signature Required {}", receiveSigRequired );
        txValidDuration = 65;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        // Create fully populated account
        tx = new AccountCreateTransaction(client)
                // The only _required_ property here is `key`
                .setKey(newKey.getPublicKey())
                .setInitialBalance(initialBalance)
                .setTransactionFee(txFee)
                .setAutoRenewPeriod(Duration.ofSeconds(autoRenewSeconds))
                .setMemo(memo)
                .setProxyAccountId(AccountId.fromString(proxy))
                .setReceiveRecordThreshold(receiveThreshold)
                .setReceiverSignatureRequired(true)
                .setSendRecordThreshold(sendThreshold)
                .setTransactionValidDuration(Duration.ofSeconds(txValidDuration));

        var accountId = tx.sign(newKey).executeForReceipt().getAccountId();
        log.info("Output > Account ID {}", accountId.toString());

        // transfer crypto no memo
        logTitle("Crypto Transfer no memo");
        long transferAmount = 200;
        log.info("Input > Transfer amount {}", transferAmount);
        String transferTo = "0.0.99";
        log.info("Input > Transfer to {}", transferTo);
        txFee = 21_000_000;
        log.info("Input > TX fee {}", txFee);
        memo = "";
        log.info("Input > memo {}", memo);
        txValidDuration = 75;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        CryptoTransferTransaction txTransfer = new CryptoTransferTransaction(client)
        		.addRecipient(AccountId.fromString(transferTo), transferAmount)
        		.addSender(ExampleHelper.getOperatorId(), transferAmount)
        		.setTransactionFee(txFee)
        		.setMemo(memo)
        		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration));
        
        var receipt = txTransfer.executeForReceipt();
        
        // transfer crypto with memo
        logTitle("Crypto Transfer with memo");
        transferAmount = 300;
        log.info("Input > Transfer amount {}", transferAmount);
        transferTo = "0.0.98";
        log.info("Input > Transfer to {}", transferTo);
        txFee = 22_000_000;
        log.info("Input > TX fee {}", txFee);
        memo = "Crypto Transfer memo";
        log.info("Input > memo {}", memo);
        txValidDuration = 80;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        txTransfer = new CryptoTransferTransaction(client)
        		.addRecipient(AccountId.fromString(transferTo), transferAmount)
        		.addSender(ExampleHelper.getOperatorId(), transferAmount)
        		.setTransactionFee(txFee)
        		.setMemo(memo)
        		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration));
        
        receipt = txTransfer.executeForReceipt();
        
        // update account

        receipt = new AccountCreateTransaction(client)
                // The only _required_ property here is `key`
                .setKey(newKey.getPublicKey())
                .setInitialBalance(initialBalance)
                .setTransactionFee(txFee)
                .executeForReceipt();

        var accountToUpdate = receipt.getAccountId();
                
        logTitle("Crypto update");
        log.info("Updating account {} ", accountToUpdate.getAccountNum());
        autoRenewSeconds = oneDayOfSeconds * 5;
        log.info("Input > Auto Renew Seconds {}", autoRenewSeconds);
        var expirationTime = Instant.now().plusSeconds(oneDayOfSeconds * 6);
        log.info("Input > Expiration Time {}", expirationTime.getEpochSecond());
        memo = "Update account memo";
        log.info("Input > memo {}", memo);
        proxy = "0.0.5";
        log.info("Input > Proxy {}", proxy );
        receiveThreshold = 15_000;
        log.info("Input > Receive Threshold {}", receiveThreshold );
        sendThreshold = 17_000;
        log.info("Input > Send Threshold {}", sendThreshold );
//        boolean receiveSigRequired = true;
//        log.info("Input > Receiver Signature Required {}", receiveSigRequired );
        Ed25519PrivateKey updatedKey = Ed25519PrivateKey.generate();
        log.info("Input > new Key {}", updatedKey.getPublicKey() );

        txFee = 23_000_000;
        log.info("Input > TX fee {}", txFee);
        txValidDuration = 85;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);

        receipt = new AccountUpdateTransaction(client)
        		.setAccountForUpdate(accountToUpdate)
        		.setAutoRenewPeriod(Duration.ofSeconds(autoRenewSeconds))
        		.setExpirationTime(expirationTime)
        		.setMemo(memo)
        		.setProxyAccount(AccountId.fromString(proxy))
        		.setReceiveRecordThreshold(receiveThreshold)
        		.setSendRecordThreshold(sendThreshold)
        		.setKey(updatedKey.getPublicKey())
        		.setTransactionFee(txFee)
        		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration))
        		.sign(updatedKey)
        		.sign(newKey)
        		.executeForReceipt();

        receipt = new AccountCreateTransaction(client)
                // The only _required_ property here is `key`
                .setKey(newKey.getPublicKey())
                .setInitialBalance(initialBalance)
                .setTransactionFee(txFee)
                .executeForReceipt();

        var accountToDelete = receipt.getAccountId();
        
        // delete account
        logTitle("Crypto delete");
        log.info("Input > Account to delete {}", accountToDelete.toString());
        log.info("Input > Transfer to account {}", ExampleHelper.getOperatorId());
        memo = "Delete account memo";
        log.info("Input > memo {}", memo);
        
        txFee = 24_000_000;
        log.info("Input > TX fee {}", txFee);
        txValidDuration = 90;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);

        receipt = new AccountDeleteTransaction(client)
        		.setDeleteAccountId(accountToDelete)
        		.setTransferAccountId(ExampleHelper.getOperatorId())
        		.setMemo(memo)
        		.setTransactionFee(txFee)
        		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration))
        		.sign(newKey)
        		.executeForReceipt();
        
        //TODO: Account add claim x2
        //TODO: Account delete claim

        // sleep 6s to allow record file to be written
        log.info("Sleeping 6s");
        Thread.sleep(6000);
        log.info("Making 3 crypto transfers");
        // make crypto transfers to force record file close
        client.transferCryptoTo(AccountId.fromString("0.0.3"), 10_000);
        client.transferCryptoTo(AccountId.fromString("0.0.3"), 10_000);
        client.transferCryptoTo(AccountId.fromString("0.0.3"), 10_000);

        // sleep 6s to allow record file to be written
        log.info("Sleeping 6s");
        Thread.sleep(6000);
        log.info("Making 3 crypto transfers");
        client.transferCryptoTo(AccountId.fromString("0.0.3"), 10_000);
        client.transferCryptoTo(AccountId.fromString("0.0.3"), 10_000);
        client.transferCryptoTo(AccountId.fromString("0.0.3"), 10_000);

        System.out.println("Done");
    }    
    private static void logTitle(String title) {
        log.info("----------------------------------------------");
        log.info(title.toUpperCase());
        log.info("----------------------------------------------");
    }
}
