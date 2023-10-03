# Build image to format chart structure and verify templates
FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm AS build

# Install yq
RUN wget https://github.com/mikefarah/yq/releases/download/v4.35.2/yq_linux_amd64 \
    && mv yq_linux_amd64 /usr/local/bin/yq \
    && chmod +x /usr/local/bin/yq

# Pull in charts
WORKDIR /app
COPY . ./

# Merge values files
RUN yq -i '. *= load("marketplace/gcp/values.yaml")' hedera-mirror/values.yaml

ARG TAG
RUN sed -i "s/\$TAG/$TAG/g" marketplace/gcp/schema.yaml

# Run helm template to render and verify templates
RUN helm dependency update hedera-mirror
RUN helm template hedera-mirror -f hedera-mirror/values.yaml

# Build chart archive, copy hedera-mirror contents to chart dir to comply with mpdev expectations
RUN mv hedera-mirror chart && tar -czvf hedera-mirror-node.tar.gz chart

# Setup marketplace structure on helm deployer image base
FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm
COPY --from=build /app/hedera-mirror-node.tar.gz /data/chart
COPY --from=build /app/marketplace/gcp/schema-test.yaml /data-test/schema.yaml
COPY --from=build /app/marketplace/gcp/schema.yaml /data
