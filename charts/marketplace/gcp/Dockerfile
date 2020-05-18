FROM ubuntu:19.10 AS build

## pull in chart
COPY hedera-mirror /tmp/chart

# get yq and merge values files
RUN apt-get update \
    && apt-get install -y software-properties-common \
    && add-apt-repository ppa:rmescandon/yq \
    && apt-get install -y --no-install-recommends yq gettext-base

RUN yq m -i --overwrite /tmp/chart/values.yaml /tmp/chart/values-marketplace.yaml

# Pull in and update schema for marketplace deployer
ARG tag
COPY marketplace/gcp/schema.yaml /tmp/schema.yaml
RUN cat /tmp/schema.yaml \
    | env -i "TAG=$tag" envsubst \
    > /tmp/schema.yaml.new \
    && mv /tmp/schema.yaml.new /tmp/schema.yaml

# Setup marketplace structure on helm deployer image base
FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm
COPY --from=build /tmp/ .

# helm template to render and verify templates
RUN helm template chart -f chart/values.yaml
RUN tar -czvf hedera-mirror-node.tar.gz chart/

# move chart and schema to expected /data folder structure
RUN mv hedera-mirror-node.tar.gz /data/chart/
RUN mv schema.yaml /data/schema.yaml
RUN rm -r chart/
