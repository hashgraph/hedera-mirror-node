## Release Checklist

This checklist verifies a release is rolled out successfully.

## Preparation

- [ ] Milestone created
- [ ] Milestone field populated on
      relevant [issues](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aclosed+no%3Amilestone+sort%3Aupdated-desc)
- [ ] Nothing open
      for [milestone](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aopen+sort%3Aupdated-desc+milestone%3A0.105.0)
- [ ] GitHub checks for branch are passing
- [ ] No pre-release or snapshot dependencies present in build files
- [ ] Verify hedera-services protobuf dependency doesn't contain any unsupported fields
- [ ] Automated Kubernetes deployment to integration successful
- [ ] Tag release

## Previewnet

v1 is deployed by the above tag automation. Deploy v2 soon after v1 is successful.

- [ ] Deployed v1
- [ ] Deployed v2

## Performance

- [ ] Deployed v1
- [ ] Deployed v2
- [ ] gRPC API performance tests
- [ ] Importer performance tests

## Mainnet Staging

- [ ] Deployed v1
- [ ] Deployed v2
- [ ] REST API performance tests
- [ ] Web3 API performance tests

## Generally Available

- [ ] Tag release
- [ ] Publish release
- [ ] Publish marketplace release

## Previewnet

v1 is deployed by above GA tag automation. Deploy v2 soon after v1 is successful.

- [ ] Deployed v1
- [ ] Deployed v2

## Testnet

The GA tag automation deploys v1 to testnet-na. Upon success, deploy v1 and v2 to testnet-eu.

- [ ] Deployed v1
- [ ] Deployed v2

## Integration Docker

This preprod environment is automatically deployed for any GA release.

- [ ] Deployed

## Dev

This preprod environment is automatically deployed for any GA release.

- [ ] Deployed

## Staging Small

This environment does migration testing against testnet state. We should deploy GA versions to it after testnet is
successfully updated.

- [ ] Deployed

## Mainnet

- [ ] Deployed

## Staging Large

This environment does migration testing against mainnet state. We should deploy GA versions to it after mainnet is
successfully updated.

- [ ] Deployed

## Post Release

- [ ] Update any completed HIPs to its status to `Final` and populate the `release`.
