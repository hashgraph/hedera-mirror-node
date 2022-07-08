# GCP Marketplace

This document outlines the steps necessary to upgrade the version of the mirror node on GCP Marketplace.

## Open Source Compliance

This step should be completed first since it may take some time for Google to approve the worksheet.

- [ ] [Generate](/charts/marketplace/gcp/open-source-compliance/generate.sh) a list of java dependencies and their
  licenses
- [ ] Compare licenses with current open source compliance worksheet
- [ ] If it differs, update it and re-submit to Google

## Local Changes

- [ ] Bump PostgreSQL version in wrapper chart and in `release.sh`
- [ ] Update `schema.yaml` with release notes and any new/deleted Helm properties
- [ ] Update marketplace `values.yaml`

## Test install locally

- [ ] Build a deployer image using
  the [instructions](https://github.com/hashgraph/hedera-mirror-node/blob/master/charts/marketplace/gcp/BUILDING.md)
- [ ] Run `mpdev verify` and `mpdev install`
- [ ] Make any necessary local changes and re-test

## Test upgrade locally

- [ ] Install version from marketplace staging registry locally using mpdev install
- [ ] Upgrade to latest version

## Submit

- [ ] Release the images to the staging registry
- [ ] Re-test with those images
- [ ] Create a new version in Marketplace
- [ ] Review all other metadata in Marketplace to ensure they are up-to-date
- [ ] Approved by Google

## Test install

- [ ] Deploy the new version via Marketplace to a new GKE cluster
- [ ] Delete temporary cluster
