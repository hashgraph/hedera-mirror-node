## Release Checklist

This checklist verifies a release is rolled out successfully.

## Preparation

- [ ] Milestone created
- [ ] Milestone field populated on
      relevant [issues](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aclosed+no%3Amilestone+sort%3Aupdated-desc)
- [ ] Nothing open
      for [milestone](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aopen+sort%3Aupdated-desc+milestone%3A0.104.0)
- [ ] GitHub checks for branch are passing
- [ ] No pre-release or snapshot dependencies present in build files
- [ ] Verify hedera-services protobuf dependency doesn't contain any unsupported fields
- [ ] Tag release
- [ ] Publish marketplace release
- [ ] Publish release

## Integration

- [ ] Deployed

## Integration Docker

- [ ] Deployed

## Dev

- [ ] Deployed

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

## Staging Large

- [ ] Deployed

## Staging Small

- [ ] Deployed

## Previewnet

- [ ] Deployed v1
- [ ] Deployed v2

## Testnet

- [ ] Deployed v1
- [ ] Deployed v2

## Mainnet

- [ ] Deployed
