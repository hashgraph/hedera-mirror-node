# build image to format chart structure and verify templates
FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm AS build

# pull in chart
COPY hedera-mirror /tmp/chart

# get yq and merge values files
RUN wget https://github.com/mikefarah/yq/releases/download/2.4.0/yq_linux_amd64 \
    && mv yq_linux_amd64 /usr/local/bin/yq \
    && chmod +x /usr/local/bin/yq

RUN yq m -i --overwrite /tmp/chart/values.yaml /tmp/chart/values-marketplace.yaml

# Pull in and update schema for marketplace deployer
ARG tag
COPY marketplace/gcp/schema.yaml /tmp/schema.yaml
RUN cat /tmp/schema.yaml \
    | env -i "TAG=$tag" envsubst \
    > /tmp/schema.yaml.new \
    && mv /tmp/schema.yaml.new /tmp/schema.yaml

# helm template to render and verify templates
RUN helm template /tmp/chart -f /tmp/chart/values.yaml
RUN cd /tmp && tar -czvf hedera-mirror-node.tar.gz chart

# Setup marketplace structure on helm deployer image base
FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm
COPY --from=build /tmp/hedera-mirror-node.tar.gz /data/chart
COPY --from=build /tmp/schema.yaml /data
