## Release Checklist

This checklist verifies a release is rolled out successfully.

## Preparation

- [ ] Milestone created
- [ ] Milestone field populated on
      relevant [issues](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aclosed+no%3Amilestone+sort%3Aupdated-desc)
- [ ] Nothing open
      for [milestone](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aopen+sort%3Aupdated-desc+milestone%3A0.91.0)
- [ ] GitHub checks for branch are passing
- [ ] No pre-release or snapshot dependencies present in build files
- [ ] Automated Kubernetes deployment successful
- [ ] Tag release
- [ ] Publish marketplace release
- [ ] Publish release

## Performance

- [ ] Deployed
- [ ] gRPC API performance tests
- [ ] Importer performance tests

## Staging

- [ ] Deployed
- [ ] REST API performance tests
- [ ] Web3 API performance tests

## Previewnet

- [ ] Deployed

## Testnet

- [ ] Deployed

## Mainnet

- [ ] Deployed
