# GCP Marketplace

This document outlines the steps necessary to upgrade the version of the mirror node on GCP Marketplace.

- Adjust local Marketplace configuration
  - [ ] Bump PostgreSQL version in wrapper chart and in `release.sh`
  - [ ] Update release notes in schema.yaml
  - [ ] Update marketplace values.yaml
  - [ ] Update schema.yaml
- Test install locally
  - [ ] Build a deployer image using
    the [instructions](https://github.com/hashgraph/hedera-mirror-node/blob/master/charts/marketplace/gcp/BUILDING.md)
  - [ ] Run mpdev verify and install
  - [ ] Make any necessary local changes and re-test
- Test upgrade locally
  - [ ] Install version from marketplace staging registry locally using mpdev install
  - [ ] Upgrade to latest version
- Open Source Compliance
  - [ ] [Generate](/charts/marketplace/gcp/open-source-compliance/generate.sh) a list of java dependencies and their
    licenses
  - [ ] Compare licenses with current open source compliance worksheet
  - [ ] If it differs, update it and re-submit to Google
- Submit
  - [ ] Release the images to staging registry
  - [ ] Re-test with those images
  - [ ] Create a new version in Marketplace
  - [ ] Review all other metadata in Marketplace to ensure they are up to date
  - [ ] Approved by Google
- Test install
  - [ ] Deploy the current version to a new GKE cluster
  - [ ] Delete temporary cluster
- Test upgrade
  - [ ] Deploy the current version to a new GKE cluster
  - [ ] Delete temporary cluster
