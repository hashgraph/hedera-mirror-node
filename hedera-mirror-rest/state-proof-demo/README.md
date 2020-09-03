# Hedera Mirror State Proof Alpha

The Mirror Node State Proof Alpha provides the ability to cryptographically prove a transaction is valid on Hedera Network.
It's the interim solution until [full state proof](https://www.hedera.com/blog/state-proofs-on-hedera) is implemented.

Refer to the [State Proof Alpha Design](https://github.com/hashgraph/hedera-mirror-node/blob/master/docs/design/stateproofalpha.md) for more architectural details.

A node based CLI tool `check-state-proof` is provided here to showcase the steps necessary to independently check the validity of a transaction.

## Requirements
To run the CLI you must first install and then configure the REST API as explained below

### Install CLI
The node based CLI tool `check-state-proof` can be installed as follows
1. Navigate to the `hedera-mirror-rest/state-proof-demo` directory
2. Npm install the tool -  `npm install -g .`

To verify correct installation simply run `check-state-proof` or `npm start` with no parameters.
The following help section will be displayed to showcase usage
```.env
Usage: -t <transactionId> -e <env>

Options:
  --help               Show help                                       [boolean]
  --version            Show version number                             [boolean]
  -t, --transactionId  Your Hedera Network Transaction Id e.g.
                       0.0.94139-1570800748-313194300        [string] [required]
  -s, --sample         Use sample data                                 [boolean]
  -e, --env            Your environment e.g. test / main     [string] [required]

Missing required arguments: t, e
```

``

### Configure REST API
To enable State Proof logic the REST API configurations must updated to allow for communication with cloud buckets to pull down the necessary files (address book, signatures files and record file).
The process involves setting the properties under `hedera.mirror.rest.stateproof` as documented at [REST API Config](https://github.com/hashgraph/hedera-mirror-node/blob/master/docs/configuration.md#rest-api)

An example configuration is provided below

```.env
hedera:
  mirror:
    rest:
      stateproof:
        enabled: true
        streams:
          network: 'TESTNET'
          cloudProvider: 'GCP'
          region: 'us-east-1'
          accessKey: <accessKey>
          secretKey: <secretKey>
          bucketName: 'hedera-stable-testnet-streams-2020-08-27'
```

## Run Check-State-Proof CLI
From command line run

`check-state-proof -t <transactionId> -h <host> -e <environment> -f <filePath>`

- t - The transactionId to be verified
- e - Hedera network environment to point at e.g. previewnet | mainnet | testnet
- h - The host of the REST API endpoint e.g. https://testnet.mirrornode.hedera.com (also the default). This overrides the value of the environment.


> **_Note_** `npm start --` may be used in favor of `check-state-proof` if you don't install the tool globally
e.g. `npm start -- -t <transactionId> -h <host> -e <previewnet|mainnet|testnet> -f <filePath>`


### Sample Case
To verify the sample case run the following command

`check-state-proof -t 0.0.94139-1570800748-313194300 -f <absoluteDirPath>/stateProofSample.json`

> **_Note_** The -f option requires an absolute filePath to be provided


### Testnet Case

`check-state-proof -t <transactionId> -e testnet`

### Mainnet Case

`check-state-proof -t <transactionId> -e mainnet`

### Custom Endpoint Case

`check-state-proof -t <transactionId> -h <http(s)Endpoint>`

## State Proof Logic
The CLI takes the following steps to prove legitimacy of provided transaction ID

1. Obtains user input of transactionId and other params

2. Makes a REST API call to the mirror node to retrieve stateproof supporting files - addressBook(s), signature files and a record file.

3. Store files locally and verifies at least 1 addressBook, 2 signatures, 1 rcd file were retrieved.

4. Parses AddressBook(s) pulling out and creating a map of nodeIds to public keys

5. Parses signature file buffer pulling out signature and hash from each file

6. Parses record file pulling out file hash and a map of transactionsIds

7. Verified the record file contains the requested transactionId

8. Verifies the public keys of each node were used to sign the hashes noted in the signature files and produced the provided signatures.

9. Verifies there is a hash from the signatures files that is matched by at least 1/3 of the nodes.

10. Verified the record file hash matches the hash that reached 1/3 consensus from nodes.

11. Returns true if all  verifications pass

