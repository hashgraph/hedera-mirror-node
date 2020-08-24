# Hedera Mirror State Proof

## State Proof Logic
The CLI takes the following steps to prove legitimacy of provided transaction ID

Step 1: Obtains user input of transactionId and other params

Step 2: Makes a REST API call to the mirror node to retrieve stateproof supporting files - addressBook(s), signature files and a record file.

Step 3 : Store files locally and verifies at least 1 addressBook, 2 signatures, 1 rcd file were retrieved.

Step 4: Parses AddressBook(s) pulling out and creating a map of nodeIds to public keys

Step 5: Parses signature file buffer pulling out signature and hash from each file

Step 6: Parses record file pulling out file hash and a map of transactionsIds

Step 7: Verified the record file contains the requested transactionId

Step 8: Verifies the public keys of each node were used to sign the hashes noted in the signature files and produced the provided signatures.

Step 9: Verifies there is a hash from the signatures files that is matched by at least 1/3 of the nodes.

Step 10: Verified the record file hash matches the hash that reached 1/3 consensus from nodes.

Step 11: Returns true if all  verifications pass

## Installation
From `state-proof-demo/` run

`npm install -g .`

## Run
From command line run

`state-proof -t <transactionId> -e <env> -sample <true|false>`

### Sample Case
To verify the sample case run teh following command

`state-proof -t 0.0.94139-1570800748-313194300 -e dev -sample true`

### Testnet Case

`state-proof -t <transactionId> -e testnet`

### Mainnet Case

`state-proof -t <transactionId> -e mainnet`
