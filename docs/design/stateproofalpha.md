# State Proof Alpha Design

## Purpose

State Proof Alpha provides the data to prove a transaction is valid on Hedera Network. It's the interim solution
until [full state proof](https://www.hedera.com/blog/state-proofs-on-hedera) is implemented.

## Goals

- Provide a State Proof REST API for clients to retrieve the record file containing the transaction, the corresponding
signature files, and the address book at the time of the transaction to prove its validity


## REST API

```
GET /transactions/:transactionId/stateproof
```

where `transactionId` is in the format of `shard.realm.num-sss-nnn`, in which `sss` are seconds and `nnn` are
nanoseconds of the valid start timestamp of the transaction.

The response is in JSON:

```json
{
    "record_file": "record file content",
    "address_books": [
      "address book content",
    ],
    "signature_files": {
      "0.0.3": "signature file content of node 0.0.3",
      "0.0.4": "signature file content of node 0.0.4",
      "0.0.n": "signature file content of node 0.0.n"
    }
}
```

- All file content is in base64 encoding

Upon receiving the JSON response, a client proves the transaction is valid as follows:

1. Validate the address book
1. Validate the signatures of the record file using the validated address book
1. Validate the record file
1. Parse the record file and check for the transaction in the parsed records

## Architecture
![Architecture](images/state-proof-alpha-architecture.png)

1. Client requests state proof data for a transaction from state proof alpha REST API
2. State proof alpha REST API queries data for the transaction from PostgreSQL: the name of the record file containing
the transaction, and the address book at the consensus timestamp of the transaction
3. State proof alpha REST API downloads the record file and the corresponding signature files from S3
4. State proof alpha REST API sends the record file, signature files, and address book to Client

### Sequence Diagram
![Sequence Diagram](images/state-proof-alpha-sequence.svg)
