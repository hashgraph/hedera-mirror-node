# Stream Verification

## Problem
Stream files need to be verified for correctness or to identify a particular error observed when processing in an
environment.

## Setup Database

1. cd {HEDERA_REPO}
2. rm -r db
3. docker-compose up db

## Configuration
Create application.yml in {HEDERA_REPO}/hedera-mirror-importer/config/

1. Configure Network
2. Configure start date
3. Configure end date (optional)
4. configure bucketName (blank will use the default bucket for the network)
### For S3 download application.yml
```yaml
hedera:
  mirror:
    importer:
      startDate: 2023-04-19T16:12:00.000000000Z # exclusive
      endDate: 2023-04-19T16:12:00.000000001Z # inclusive
      network: testnet
      downloader:
        cloudProvider: S3
        accessKey: <S3_ACCESS_KEY>
        secretKey: <S3_SECRET_KEY>
        bucketName: # Remove or use a known bucket name
      parser:
        record:
          sidecar: true
```

### For Local Files application.yml
You will need the record file(s) and signature file(s) from at least 1/3 of the nodes and placed under the corresponding
node folder in `${hedera.mirror.importer.downloader.mirrorProperties.dataPath}/streams/{accountBalances|recordstreams}/{NODE_FOLDER}`
```yaml
hedera:
  mirror:
    importer:
      startDate: 2023-04-19T22:47:16.030078003Z # exclusive
      endDate: 2023-04-19T22:47:16.030078003Z # inclusive
      network: testnet
      downloader:
        cloudProvider: LOCAL
        mirrorProperties:
          dataPath: {PATH_TO_RECORD_STREAMS} # will create sub dirs /streams/accountBalances/record{NODE1...NODEn} /streams/recordstreams/record{NODE1...NODEn}. Must have the signature file and the record file for 1/3 of nodes configured in address book
      parser:
        record:
          sidecar: true
```
## Run the application
1. cd {HEDERA_REPO}
2. `./gradlew importer:bootRun`
3. Optionally, run in an IDE and run the bootrun gradle task on the importer module

## Verify Data
1. `psql -U mirror_node`
2. password is located in docker-compose.yml in this repo and defaulted to `mirror_node_pass`
