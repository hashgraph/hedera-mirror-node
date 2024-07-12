## Release Checklist

This checklist verifies a release is rolled out successfully.

## Preparation

- [ ] Milestone created
- [ ] Milestone field populated on relevant [issues](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aclosed+no%3Amilestone+sort%3Aupdated-desc)
- [ ] Nothing open for [milestone](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aopen+sort%3Aupdated-desc+milestone%3A0.105.0)
- [ ] GitHub checks for branch are passing
- [ ] No pre-release or snapshot dependencies present in build files
- [ ] Verify hedera-services protobuf dependency doesn't contain any unsupported fields or messages
- [ ] Automated Kubernetes deployment to integration successful
- [ ] Tag release

## Release Candidates

### Previewnet

v1 is deployed by tag creation automation. Upon success, a PR for v2 will automatically get created.

- [ ] Deployed v1
- [ ] Deployed v2

### Performance

- [ ] Deployed v1
- [ ] Deployed v2
- [ ] gRPC API performance tests
- [ ] Importer performance tests

### Mainnet Staging

- [ ] Deployed v1
- [ ] Deployed v2
- [ ] REST API performance tests
- [ ] Web3 API performance tests

## Generally Available

- [ ] Publish release
- [ ] Publish marketplace release

### Previewnet

v1 is deployed by tag creation automation. Upon success, a PR for v2 will automatically get created.

- [ ] Deployed v1
- [ ] Deployed v2

### Testnet

The GA tag automation deploys v1 to NA. Upon success, a PR for EU will automatically get created.

- [ ] Deployed NA
- [ ] Deployed EU

### Pre-Production

These preprod environments are automatically deployed for any GA release. Ensure the deployments are successful.

- [ ] Dev
- [ ] Integration Docker
- [ ] Staging Large
- [ ] Staging Small

### Mainnet

Wait about a week after the testnet deployment to give it time to bake, then deploy to NA first. Upon success, a PR for
EU will automatically get created.

- [ ] Deployed NA
- [ ] Deployed EU

## Post Release

- [ ] Update any completed HIPs to `Final` status and populate the `release`.
