## Release Checklist

This checklist verifies a release is rolled out successfully.

## Preparation

- [ ] Milestone field populated on
  relevant [issues](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aclosed+no%3Amilestone+sort%3Aupdated-desc)
- [ ] Nothing open
  for [milestone](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aopen+sort%3Aupdated-desc+milestone%3A0.76.0)
- [ ] GitHub checks for branch are passing
- [ ] No pre-release or snapshot dependencies present in build files
- [ ] Automated Kubernetes deployment successful
- [ ] Tag release
- [ ] Upload release artifacts
- [ ] Manual Submission for GCP Marketplace verification by google
- [ ] Publish marketplace release
- [ ] Publish release

## Performance

- [ ] Deployed
- [ ] gRPC API performance tests
- [ ] Importer performance tests
- [ ] REST API performance tests

## Previewnet

- [ ] Deployed

## Staging

- [ ] Deployed

## Testnet

- [ ] Deployed

## Mainnet

- [ ] Deployed to public
- [ ] Deployed to private
