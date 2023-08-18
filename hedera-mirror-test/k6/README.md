# K6 Performance Tests

This module covers the [k6](https://k6.io/) based performance tests for Mirror Node APIs including rest, rosetta,
and web3.

## Setup

The k6 test engine is needed to run the tests. Please follow
the [official documentation](https://k6.io/docs/getting-started/installation/) to install k6. If running on a VM,
ensure the following OS properties are set to avoid resource exhaustion:

```shell
ulimit -n 1048576
echo "1" > /proc/sys/net/ipv4/tcp_tw_reuse
echo "16384 65535" > /proc/sys/net/ipv4/ip_local_port_range
```

## Run The Tests

The tests are organized per API, and they reside in `src/rest`, `src/rosetta`, and `src/web3` respectively. You can run
the tests of an API as a test suite. You can also run tests one at a time.

### Test Suite

To run a test suite, such as rest, use the following command.

```shell
DEFAULT_DURATION=1s \
DEFAULT_VUS=1 \
BASE_URL=https://testnet.mirrornode.hedera.com \
DEFAULT_LIMIT=100 \
DEFAULT_START_ACCOUNT=0.0.34196600 k6 run src/rest/apis.js
```

Another option is to have a parameters file named `parameters.env` with the content:

```shell
export DEFAULT_DURATION=1s
export DEFAULT_VUS=1
export BASE_URL=https://testnet.mirrornode.hedera.com
export DEFAULT_LIMIT=100
export DEFAULT_START_ACCOUNT=0.0.34196600
```

And execute k6 after exporting the values for the env variables:

```shell
source parameters.env
k6 run src/rest/apis.js
```

For non domain specific parameters like:

- DEFAULT_DURATION
- DEFAULT_VUS
- BASE_URL
- DEFAULT_LIMIT

The value can be set via environment variables. If no value is set, then a sane default will be used.

For domain specific parameters the following rule is used:
When the value of a parameter is set with an environment variable, the value will be used, but if no value is set for a
particular parameter, then its value will be found by querying either the rest or rosetta APIs.

The default timeout set to discover the parameters is 5 minutes, increase `DEFAULT_SETUP_TIMEOUT` if needed.

The following parameters can be used to configure a rest test:

- DEFAULT_ACCOUNT_ID
- DEFAULT_ACCOUNT_ID_NFTS
- DEFAULT_ACCOUNT_ID_TOKEN
- DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE
- DEFAULT_ACCOUNT_BALANCE
- DEFAULT_BALANCE_TIMESTAMP
- DEFAULT_BLOCK_NUMBER
- DEFAULT_BLOCK_HASH
- DEFAULT_CONTRACT_ID
- DEFAULT_CONTRACT_TIMESTAMP
- DEFAULT_CONTRACT_RESULT_HASH
- DEFAULT_NFT_ID
- DEFAULT_NFT_SERIAL
- DEFAULT_PUBLIC_KEY
- DEFAULT_SCHEDULE_ACCOUNT_ID
- DEFAULT_SCHEDULE_ID
- DEFAULT_TOKEN_BALANCE_TIMESTAMP
- DEFAULT_TOKEN_ID
- DEFAULT_TOPIC_ID
- DEFAULT_TOPIC_SEQUENCE
- DEFAULT_TOPIC_TIMESTAMP
- DEFAULT_TRANSACTION_HASH
- DEFAULT_TRANSACTION_ID

The following parameters can be used to configure a rosetta test:

- DEFAULT_BLOCK_INDEX
- DEFAULT_BLOCK_HASH
- DEFAULT_NETWORK
- DEFAULT_TRANSACTION_HASH

The following parameters can be used to configure a web3 test:

- ACCOUNT_ADDRESS - 64 character hex encoded account address without `0x` prefix
- DEFAULT_ACCOUNT_ADDRESS - 64 character hex encoded account address without `0x` prefix
- DEFAULT_CONTRACT_ADDRESS - 40 character hex encoded contract address without `0x` prefix (Parent contract should be deployed)
- ERC_CONTRACT_ADDRESS - 40 character hex encoded contract address without `0x` prefix (ErcTestContract contract in hedera-mirror-test/src/test/resources/solidity/contracts/ErcTestContract.sol should be deployed)
- HTS_CONTRACT_ADDRESS - 40 character hex encoded contract address without `0x` prefix (PrecompileTestContract contract in hedera-mirror-test/src/test/resources/solidity/contracts/PrecompileTestContract.sol should be deployed)
- KEY_TYPE - 64 character hex encoded key type without `0x` prefix
- OPERATOR_ADDRESS - 64 character hex encoded account address without `0x` prefix
- SERIAL_NUMBER - 64 character hex encoded nft serial number without `0x` prefix
- SPENDER_ADDRESS - 64 character hex encoded account address without `0x` prefix
- TOKEN_ADDRESS - 64 character hex encoded token address without `0x` prefix

For k6 to be run we need to deploy contracts first. For that, we can use Hedera SDK.
Example for ERC_CONTRACT deployment with js SDK

```js
const contractCreate = await new ContractCreateFlow()
  .setBytecode(
    "HERE YOU NEED TO PUT BYTECODE FROM hedera-mirror-test/src/test/resources/solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json"
  )
  .setGas(200_000)
  .execute(client);
```

The test suite will run the tests sequentially with a configurable graceful stop time in between, so they don't
interfere with each other.

Once the tests complete, `k6` will show a summary report.

```
          /\      |‾‾| /‾‾/   /‾‾/
     /\  /  \     |  |/  /   /  /
    /  \/    \    |     (   /   ‾‾\
   /          \   |  |\  \ |  (‾)  |
  / __________ \  |__| \__\ \_____/ .io

  execution: local
     script: apis.js
     output: -

  scenarios: (100.00%) 11 scenarios, 500 max VUs, 11m55s max duration (incl. graceful stop):
           * accountBalance: 500 looping VUs for 1m0s (exec: run, gracefulStop: 5s)
           * block: 500 looping VUs for 1m0s (exec: run, startTime: 1m5s, gracefulStop: 5s)
           * blockTransaction: 500 looping VUs for 1m0s (exec: run, startTime: 2m10s, gracefulStop: 5s)
           * constructionCombine: 500 looping VUs for 1m0s (exec: run, startTime: 3m15s, gracefulStop: 5s)
           * constructionHash: 500 looping VUs for 1m0s (exec: run, startTime: 4m20s, gracefulStop: 5s)
           * constructionParse: 500 looping VUs for 1m0s (exec: run, startTime: 5m25s, gracefulStop: 5s)
           * constructionPayloads: 500 looping VUs for 1m0s (exec: run, startTime: 6m30s, gracefulStop: 5s)
           * constructionPreprocess: 500 looping VUs for 1m0s (exec: run, startTime: 7m35s, gracefulStop: 5s)
           * networkList: 500 looping VUs for 1m0s (exec: run, startTime: 8m40s, gracefulStop: 5s)
           * networkOptions: 500 looping VUs for 1m0s (exec: run, startTime: 9m45s, gracefulStop: 5s)
           * networkStatus: 500 looping VUs for 1m0s (exec: run, startTime: 10m50s, gracefulStop: 5s)


running (11m50.2s), 000/500 VUs, 2910275 complete and 0 interrupted iterations
accountBalance         ✓ [======================================] 500 VUs  1m0s
block                  ✓ [======================================] 500 VUs  1m0s
blockTransaction       ✓ [======================================] 500 VUs  1m0s
constructionCombine    ✓ [======================================] 500 VUs  1m0s
constructionHash       ✓ [======================================] 500 VUs  1m0s
constructionParse      ✓ [======================================] 500 VUs  1m0s
constructionPayloads   ✓ [======================================] 500 VUs  1m0s
constructionPreprocess ✓ [======================================] 500 VUs  1m0s
networkList            ✓ [======================================] 500 VUs  1m0s
networkOptions         ✓ [======================================] 500 VUs  1m0s
networkStatus          ✓ [======================================] 500 VUs  1m0s
     ✓ AccountBalance OK
     ✓ Block OK
     ✓ BlockTransaction OK
     ✓ ConstructionCombine OK
     ✓ ConstructionHash OK
     ✓ ConstructionParse OK
     ✓ ConstructionPayloads OK
     ✓ ConstructionPreprocess OK
     ✓ NetworkList OK
     ✓ NetworkOptions OK
     ✓ NetworkStatus OK

     checks.........................................................: 100.00% ✓ 2910275     ✗ 0
     ✓ { scenario:accountBalance }..................................: 100.00% ✓ 84283       ✗ 0
     ✓ { scenario:blockTransaction }................................: 100.00% ✓ 171178      ✗ 0
     ✓ { scenario:block }...........................................: 100.00% ✓ 99791       ✗ 0
     ✓ { scenario:constructionCombine }.............................: 100.00% ✓ 277238      ✗ 0
     ✓ { scenario:constructionHash }................................: 100.00% ✓ 353405      ✗ 0
     ✓ { scenario:constructionParse }...............................: 100.00% ✓ 345479      ✗ 0
     ✓ { scenario:constructionPayloads }............................: 100.00% ✓ 330888      ✗ 0
     ✓ { scenario:constructionPreprocess }..........................: 100.00% ✓ 331073      ✗ 0
     ✓ { scenario:networkList }.....................................: 100.00% ✓ 389307      ✗ 0
     ✓ { scenario:networkOptions }..................................: 100.00% ✓ 346037      ✗ 0
     ✓ { scenario:networkStatus }...................................: 100.00% ✓ 181596      ✗ 0
     data_received..................................................: 11 GB   16 MB/s
     data_sent......................................................: 1.6 GB  2.2 MB/s
     http_req_blocked...............................................: avg=26.28ms  min=329.25µs med=26.12ms  max=3.09s    p(90)=45.56ms  p(95)=57.14ms
     http_req_connecting............................................: avg=26.06ms  min=294.53µs med=25.98ms  max=3.09s    p(90)=45.09ms  p(95)=56.56ms
     http_req_duration..............................................: avg=83.76ms  min=763.29µs med=51.09ms  max=6.8s     p(90)=145.26ms p(95)=225.72ms
       { expected_response:true }...................................: avg=83.76ms  min=763.29µs med=51.09ms  max=6.8s     p(90)=145.26ms p(95)=225.72ms
     ✗ { scenario:accountBalance,expected_response:true }...........: avg=351.87ms min=23.48ms  med=249.8ms  max=4.23s    p(90)=726.13ms p(95)=978.25ms
     ✗ { scenario:block,expected_response:true }....................: avg=300.1ms  min=16.65ms  med=185.37ms max=6.8s     p(90)=507.75ms p(95)=889.7ms
     ✗ { scenario:blockTransaction,expected_response:true }.........: avg=164.43ms min=6.05ms   med=108.42ms max=3.35s    p(90)=330.46ms p(95)=525.31ms
     ✓ { scenario:constructionCombine,expected_response:true }......: avg=73.77ms  min=1.06ms   med=65.47ms  max=729.79ms p(90)=121.4ms  p(95)=147.72ms
     ✓ { scenario:constructionHash,expected_response:true }.........: avg=49.23ms  min=813.14µs med=43.48ms  max=485.16ms p(90)=82.14ms  p(95)=100.07ms
     ✓ { scenario:constructionParse,expected_response:true }........: avg=50.64ms  min=888.63µs med=45.11ms  max=528.51ms p(90)=81.39ms  p(95)=100.5ms
     ✓ { scenario:constructionPayloads,expected_response:true }.....: avg=56.43ms  min=1.17ms   med=49.81ms  max=529.69ms p(90)=90.18ms  p(95)=111.18ms
     ✓ { scenario:constructionPreprocess,expected_response:true }...: avg=52.87ms  min=1.08ms   med=47.77ms  max=486.11ms p(90)=82.71ms  p(95)=103.17ms
     ✓ { scenario:networkList,expected_response:true }..............: avg=44.76ms  min=763.29µs med=40.33ms  max=469.95ms p(90)=67.94ms  p(95)=84.78ms
     ✓ { scenario:networkOptions,expected_response:true }...........: avg=51.96ms  min=1.74ms   med=46.75ms  max=477.27ms p(90)=79.69ms  p(95)=97.45ms
     ✓ { scenario:networkStatus,expected_response:true }............: avg=160.1ms  min=7.16ms   med=108.19ms max=2.52s    p(90)=321.2ms  p(95)=490.1ms
     http_req_failed................................................: 0.00%   ✓ 0           ✗ 2910275
     http_req_receiving.............................................: avg=7.96ms   min=24.5µs   med=5.23ms   max=481.33ms p(90)=18.99ms  p(95)=25.13ms
     http_req_sending...............................................: avg=7.43ms   min=19.24µs  med=4.86ms   max=492.96ms p(90)=17.78ms  p(95)=23.77ms
     http_req_tls_handshaking.......................................: avg=0s       min=0s       med=0s       max=0s       p(90)=0s       p(95)=0s
     http_req_waiting...............................................: avg=68.35ms  min=455.36µs med=35.52ms  max=6.79s    p(90)=128.24ms p(95)=220.83ms
     http_reqs......................................................: 2910275 4097.974399/s
     ✓ { scenario:accountBalance }..................................: 84283   118.679361/s
     ✓ { scenario:blockTransaction }................................: 171178  241.036693/s
     ✓ { scenario:block }...........................................: 99791   140.516262/s
     ✓ { scenario:constructionCombine }.............................: 277238  390.380368/s
     ✓ { scenario:constructionHash }................................: 353405  497.631544/s
     ✓ { scenario:constructionParse }...............................: 345479  486.4709/s
     ✓ { scenario:constructionPayloads }............................: 330888  465.925231/s
     ✓ { scenario:constructionPreprocess }..........................: 331073  466.185731/s
     ✓ { scenario:networkList }.....................................: 389307  548.18535/s
     ✓ { scenario:networkOptions }..................................: 346037  487.256623/s
     ✓ { scenario:networkStatus }...................................: 181596  255.706337/s
     iteration_duration.............................................: avg=113.49ms min=1.8ms    med=81.45ms  max=6.8s     p(90)=179.94ms p(95)=244.44ms
     iterations.....................................................: 2910275 4097.974399/s
     scenario_duration..............................................: 60166   min=38        max=60634
     ✓ { scenario:accountBalance }..................................: 60634   min=139       max=60634
     ✓ { scenario:blockTransaction }................................: 60253   min=40        max=60253
     ✓ { scenario:block }...........................................: 60282   min=112       max=60282
     ✓ { scenario:constructionCombine }.............................: 60068   min=39        max=60068
     ✓ { scenario:constructionHash }................................: 60136   min=41        max=60136
     ✓ { scenario:constructionParse }...............................: 60200   min=38        max=60200
     ✓ { scenario:constructionPayloads }............................: 60071   min=53        max=60071
     ✓ { scenario:constructionPreprocess }..........................: 60034   min=41        max=60034
     ✓ { scenario:networkList }.....................................: 60086   min=43        max=60086
     ✓ { scenario:networkOptions }..................................: 60041   min=40        max=60041
     ✓ { scenario:networkStatus }...................................: 60166   min=66        max=60166
     vus............................................................: 500     min=0         max=500
     vus_max........................................................: 500     min=500       max=500  ERRO[0719] some thresholds have failed
```

Note: disregard the per scenario RPS reported in the `http_reqs` section since it's calculated as the total requests in
a scenario divided by the run time of the test suite.

With the test suite mode, a simplified markdown format report `report.md` will also be generated.

| URL                      | VUS | Pass%  | RPS       | Avg. Req Duration | Skipped? | Comment |
| ------------------------ | --- | ------ | --------- | ----------------- | -------- | ------- |
| /account/balance         | 500 | 100.00 | 1390.03/s | 351.87ms          | No       |         |
| /block                   | 500 | 100.00 | 1655.40/s | 300.11ms          | No       |         |
| /block/transaction       | 500 | 100.00 | 2840.99/s | 164.44ms          | No       |         |
| /construction/combine    | 500 | 100.00 | 4615.40/s | 73.77ms           | No       |         |
| /construction/hash       | 500 | 100.00 | 5876.76/s | 49.23ms           | No       |         |
| /construction/parse      | 500 | 100.00 | 5738.85/s | 50.65ms           | No       |         |
| /construction/payloads   | 500 | 100.00 | 5508.28/s | 56.44ms           | No       |         |
| /construction/preprocess | 500 | 100.00 | 5514.76/s | 52.88ms           | No       |         |
| /network/list            | 500 | 100.00 | 6479.16/s | 44.77ms           | No       |         |
| /network/options         | 500 | 100.00 | 5763.35/s | 51.97ms           | No       |         |
| /network/status          | 500 | 100.00 | 3018.25/s | 160.10ms          | No       |         |

### Single Test

To run a single test, such as the rosetta accountBalance test, just do

```shell
source src/rosetta/k6.env
k6 run src/rosetta/test/accountBalance.js
```

When it completes, k6 will show a similar summary report. However, there won't be a `report.md` report.
