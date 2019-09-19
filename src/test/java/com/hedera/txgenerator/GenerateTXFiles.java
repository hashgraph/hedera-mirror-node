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
public final class GenerateTXFiles {
    private GenerateTXFiles() { }

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
        
        
        logTitle("Create file");
        String fileContents = "Hedera hashgraph is great!";
        log.info ("Input > file contents {}", fileContents);
        long duration = 2592000;
        log.info("Input > Duration {}",  duration);
        log.info("Input > Key {}", ExampleHelper.getOperatorKey().getPublicKey());
        long txFee = 100_000_000;
        log.info("Input > Transaction Fee {}",  txFee);
        String memo = "File create memo";
        log.info("Input > memo {}", memo);
        long txValidDuration = 95;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        FileCreateTransaction txFile = new FileCreateTransaction(client).setExpirationTime(
            Instant.now()
                .plus(Duration.ofSeconds(duration)))
            // Use the same key as the operator to "own" this file
            .addKey(ExampleHelper.getOperatorKey().getPublicKey())
            .setContents(fileContents.getBytes())
            .setMemo(memo)
            .setTransactionFee(txFee)
    		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration));

        var fileId = txFile.sign(newKey).executeForReceipt().getFileId();
        log.info("Output > File ID {}", fileId.toString());

        logTitle("Create file 2");
        fileContents = "Hedera hashgraph is great!";
        log.info ("Input > file contents {}", fileContents);
        duration = 2592000;
        log.info("Input > Duration {}",  duration);
        log.info("Input > Key {}", ExampleHelper.getOperatorKey().getPublicKey());
        txFee = 100_000_000;
        log.info("Input > Transaction Fee {}",  txFee);
        memo = "File create memo";
        log.info("Input > memo {}", memo);
        txValidDuration = 95;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        txFile = new FileCreateTransaction(client).setExpirationTime(
            Instant.now()
                .plus(Duration.ofSeconds(duration)))
            // Use the same key as the operator to "own" this file
            .addKey(ExampleHelper.getOperatorKey().getPublicKey())
            .setContents(fileContents.getBytes())
            .setMemo(memo)
            .setTransactionFee(txFee)
    		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration));

        fileId = txFile.sign(newKey).executeForReceipt().getFileId();
        log.info("Output > File ID {}", fileId.toString());
        
        logTitle("Append file");
        log.info("Input > File ID {}", fileId.toString());
        fileContents = "... but it gets better !";
        log.info ("Input > file contents {}", fileContents);
        memo = "File append memo 1";
        log.info("Input > memo {}", memo);
        txFee = 110_000_000;
        log.info("Input > Transaction Fee {}",  txFee);
        txValidDuration = 100;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        var txAppend = new FileAppendTransaction(client)
        		.setContents(fileContents.getBytes())
        		.setFileId(fileId)
        		.setMemo(memo)
        		.setTransactionFee(txFee)
        		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration))
        		.executeForReceipt();

        logTitle("Append file");
        log.info("Input > File ID {}", fileId.toString());
        fileContents = "... and better !";
        log.info ("Input > file contents {}", fileContents);
        memo = "File append memo 2";
        log.info("Input > memo {}", memo);
        txFee = 120_000_000;
        log.info("Input > Transaction Fee {}",  txFee);
        txValidDuration = 110;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        txAppend = new FileAppendTransaction(client)
        		.setContents(fileContents.getBytes())
        		.setFileId(fileId)
        		.setMemo(memo)
        		.setTransactionFee(txFee)
        		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration))
        		.executeForReceipt();

        // file update
        logTitle("Create file 3");
        fileContents = "Hedera hashgraph is great!";
        log.info ("Input > file contents {}", fileContents);
        duration = 2592000;
        log.info("Input > Duration {}",  duration);
        log.info("Input > Key {}", ExampleHelper.getOperatorKey().getPublicKey());
        txFee = 100_000_000;
        log.info("Input > Transaction Fee {}",  txFee);
        memo = "File create memo";
        log.info("Input > memo {}", memo);
        txValidDuration = 95;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        txFile = new FileCreateTransaction(client).setExpirationTime(
            Instant.now()
                .plus(Duration.ofSeconds(duration)))
            // Use the same key as the operator to "own" this file
            .addKey(ExampleHelper.getOperatorKey().getPublicKey())
            .setContents(fileContents.getBytes())
            .setMemo(memo)
            .setTransactionFee(txFee)
    		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration));

        fileId = txFile.sign(newKey).executeForReceipt().getFileId();
        log.info("Output > File ID {}", fileId.toString());

        logTitle("Update file");
        log.info("Input > File ID {}", fileId.toString());
        fileContents = "So good";
        log.info ("Input > file contents {}", fileContents);
        Instant expirationTime = Instant.now().plusSeconds(oneDayOfSeconds * 6);
        log.info("Input > Expiration Time {}", expirationTime.getEpochSecond());
        memo = "File update memo";
        log.info("Input > memo {}", memo);
        txFee = 130_000_000;
        log.info("Input > Transaction Fee {}",  txFee);
        txValidDuration = 115;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        var txUpdate = new FileUpdateTransaction(client)
        		.setContents(fileContents.getBytes())
        		.setExpirationTime(expirationTime)
        		.setFileId(fileId)
        		.setMemo(memo)
        		.setTransactionFee(txFee)
        		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration))
        		.addKey(newPublicKey)
        		.sign(newKey)
        		.executeForReceipt();

		// file delete
        logTitle("Create file 3");
        fileContents = "Hedera hashgraph is great!";
        log.info ("Input > file contents {}", fileContents);
        duration = 2592000;
        log.info("Input > Duration {}",  duration);
        log.info("Input > Key {}", ExampleHelper.getOperatorKey().getPublicKey());
        txFee = 100_000_000;
        log.info("Input > Transaction Fee {}",  txFee);
        memo = "File create memo";
        log.info("Input > memo {}", memo);
        txValidDuration = 95;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        
        txFile = new FileCreateTransaction(client).setExpirationTime(
            Instant.now()
                .plus(Duration.ofSeconds(duration)))
            // Use the same key as the operator to "own" this file
            .addKey(ExampleHelper.getOperatorKey().getPublicKey())
            .setContents(fileContents.getBytes())
            .setMemo(memo)
            .setTransactionFee(txFee)
    		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration));

        fileId = txFile.sign(newKey).executeForReceipt().getFileId();
        log.info("Output > File ID {}", fileId.toString());
        
        logTitle("Delete file");
        log.info("Input > deleting file {}.{}.{}", fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum());
        txFee = 140_000_000;
        log.info("Input > Transaction Fee {}",  txFee);
        txValidDuration = 120;
        log.info("Input > Transaction Valid Duration {}", txValidDuration);
        memo = "File delete memo";
        log.info("Input > memo {}", memo);
        
        var txDelete = new FileDeleteTransaction(client)
        		.setFileId(fileId)
        		.setMemo(memo)
        		.setTransactionFee(txFee)
        		.setTransactionValidDuration(Duration.ofSeconds(txValidDuration));
        txDelete.executeForReceipt();
        
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
