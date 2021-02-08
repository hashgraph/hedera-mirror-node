# Hedera Mirror State Proof Alpha

The Mirror Node State Proof Alpha provides the ability to cryptographically prove a transaction is valid on Hedera Network.
It's the interim solution until [full state proof](https://www.hedera.com/blog/state-proofs-on-hedera) is implemented.

Refer to the [State Proof Alpha Design](https://github.com/hashgraph/hedera-mirror-node/blob/master/docs/design/stateproofalpha.md) for more architectural details.

A node based CLI tool `check-state-proof` is provided here to showcase the steps necessary to independently check the validity of a transaction.

## Logic
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

### Record stream file and signature file version 5

From hedera-services v0.11.0, the mainnet has upgraded to generate version 5 record streams. While
the state proof check login remains the same, the following changes are made to support the file version upgrade:

1. Parsing logic for the new format is added. Please refer to the [official documentation](https://docs.hedera.com/guides/docs/record-and-event-stream-file-formats)
   for the format.
1. Metadata hash signature in signature file version 5 is verified in addition to file hash signature.

## Requirements
To run the CLI you must
1. Install the node application
2. Point to a mirror node with the State Proof Alpha REST API enabled.

If you would like to configure your mirror node to support State Proof Alpha you can follow the configuration steps [Enable State Proof Alpha](../../docs/configuration.md#enable-state-proof-alpha)

### Install CLI
The node based CLI tool `check-state-proof` can be installed as follows
1. Ensure you've installed [NodeJS](https://nodejs.org/en/about/)
2. Navigate to the `hedera-mirror-rest/check-state-proof` directory
3. Npm install the tool -  `npm install -g .`

To verify correct installation simply run `check-state-proof --help` or `npm start -- --help` to show usage instructions.

## Run Check-State-Proof CLI
From command line run

`check-state-proof -t <transactionId> -h <host> -e <environment> -f <filePath>`

Usage options include
```.env
  --help               Show help                                       [boolean]
  --version            Show version number                             [boolean]
  -t, --transactionId  Your Hedera Network Transaction Id e.g.
                       0.0.94139-1570800748-313194300        [string] [required]
  -f, --file           Absolute file path containing State Proof REST API
                       response json                                    [string]
  -h, --host           REST API host. Default is testnet                [string]
  -e, --env            Your environment e.g. local|mainnet|previewnet|testnet
                                                                        [string]
```

> **_Note 1:_** The host value overrides the value of the environment.

> **_Note 2:_** `npm start --` may be used in favor of `check-state-proof` if you don't install the tool globally
e.g. `npm start -- -t <transactionId> -h <host> -e <previewnet|mainnet|testnet> -f <filePath>`


### Sample Case
To verify the sample case run the following command

`check-state-proof -t 0.0.94139-1570800748-313194300 -f <absoluteDirPath>/stateProofSample.json`

> **_Note_** The -f option requires an absolute filePath to be provided


### Environment
The tool can be run against a public environment as follows

`check-state-proof -t <transactionId> -e <previewnet|mainnet|testnet>`

### Custom Endpoint Case
The tool can be run against a custom endpoint as follows

`check-state-proof -t <transactionId> -h http://127.0.0.1:1234`

or

`check-state-proof -t <transactionId> -h https://my-mirror-node.com`

### Input File
The tool can be run using a provided input file containing the response from te State Proof Alpha API as follows

`check-state-proof -t <transactionId> -f <filePath>`

