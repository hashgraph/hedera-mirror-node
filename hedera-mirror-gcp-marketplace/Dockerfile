FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm AS build

## pull in chart
COPY chart /tmp/chart

# get yq and merge values files
RUN apt-get update \
    && apt-get install -y --no-install-recommends software-properties-common \
    && add-apt-repository ppa:rmescandon/yq
RUN apt update && apt install -y --no-install-recommends yq
RUN yq m -x /tmp/chart/values.yaml /tmp/chart/values-marketplace.yaml > /tmp/chart/values-merged.yaml

# helm template to render and verify templates
RUN helm template /tmp/chart -f /tmp/chart/values-merged.yaml

# overwrite values file and archive
RUN mv /tmp/chart/values-merged.yaml /tmp/chart/values.yaml
RUN cd /tmp && tar -czvf /tmp/hedera-mirror-node.tar.gz chart/

# Pull in and update schema for marketplace deployer
ARG tag
COPY schema.yaml /tmp/schema.yaml
RUN cat /tmp/schema.yaml \
    | env -i "TAG=$tag" envsubst \
    > /tmp/schema.yaml.new \
    && mv /tmp/schema.yaml.new /tmp/schema.yaml

# move chart and schema to expected /data folder structure
RUN mv /tmp/hedera-mirror-node.tar.gz /data/chart/
RUN mv /tmp/schema.yaml /data/schema.yaml
RUN rm -r /tmp
#RUN cp /data/schema.yaml /data-test/schema.yaml
