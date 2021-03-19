# Hedera Mirror Entity Info Updater

The Mirror Node Entity Info Updater provides the ability to verify if a list of entities present in the mirror db are correct and update them if they don't match the up to date network values.

A node based CLI tool `entity-info-updater` is provided here to showcase the steps necessary to independently update stale entities.

## Logic
The CLI takes the following steps

1. Attempts to read a csv of entity ids at the `hedera.mirror.entityUpdate.filePath` value provided in the `application.yml`
2. Pulls entity info for each entity from the mirror db (using a pg connection) and the network (using the js-sdk)
3. Compares each entity's db values to its network values to verify if they are equal.
    If there's a mismatch, a new entity object based on the db copy but with values updated from the network is returned.
    If there is no mismatch, the entity is skipped.
4. Updates each entity returned in step 3 in the db.

## Requirements
To run the CLI you must
1. Install the node application
2. Configure the `hedera.mirror.entityUpdate` values in the `application.yml` to point to your desired mirror db and Hedera network.

### Install CLI
The node based CLI tool `entity-info-updater` can be installed as follows
1. Ensure you've installed [NodeJS](https://nodejs.org/en/about/)
2. Navigate to the `hedera-mirror-rest/entity-info-updater` directory
3. (Optional) Npm install the tool -  `npm install -g .`

To verify correct installation simply run `entity-info-updater --help` or `npm start -- --help` to show usage instructions.

## Run Entity-Info-Updater CLI
First ensure the importer has been stopped from parsing record stream files. This ensures data won't be updated inaccurately.

Then, from command line run

`npm start`

or

`entity-info-updater`

### Environment
The tool can be run against a public environment with the following configuration

```yaml
hedera:
  mirror:
    entityUpdate:
      ...
      sdkClient:
        network: <MAINNET|TESTNET|PREVIEWNET|OTHER>
        nodeAddress: <nodeAddress>
        nodeId: <nodeId>
        operatorId: <operatorId>
        operatorKey: <operatorKey>
```

### Custom DB Endpoint Case
The tool can be run against any DB host and database as long as it's reachable. To achieve this configure the following

```yaml
hedera:
  mirror:
    entityUpdate:
      db:
        host: 127.0.0.1
        name: mirror_node
        password: mirror_node_pass
        port: 5432
        username: mirror_node
```

### Input File
The tool requires a valid input CSV file at the location `hedera.mirror.entityUpdate.filePath` provided in the `application.yml`.
The structure of the file should matching the following

| entity        | Col B | ... |
| ------------- |  ---- | --- |
| 0.0.123456    | ...   | ... |
| ...           | ...   | ... |

Currently only the first column is required and must contain valid Hedera entityIds in the form `x.y.z`

