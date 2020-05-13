FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm AS build

# get yq
RUN apt-get update \
    && apt-get install -y --no-install-recommends software-properties-common \
    && add-apt-repository ppa:rmescandon/yq
RUN apt update && apt install -y --no-install-recommends yq

## pull in chart, swap values file and archive chart
COPY chart /tmp/chart
#merge values files
RUN yq m -x /tmp/chart/values.yaml /tmp/chart/values-marketplace.yaml > /tmp/chart/values-merged.yaml
#RUN cat /tmp/chart/values-merged.yaml

# helm template to verify templates
RUN helm template /tmp/chart -f /tmp/chart/values-merged.yaml

# overwrite values file and archive
RUN mv /tmp/chart/values-merged.yaml /tmp/chart/values.yaml
RUN cd /tmp && tar -czvf /tmp/hedera-mirror-node.tar.gz chart/

# copy schema for marketplace deployer
COPY schema.yaml /tmp/schema.yaml

ARG tag
RUN cat /tmp/schema.yaml \
    | env -i "TAG=$tag" envsubst \
    > /tmp/schema.yaml.new \
    && mv /tmp/schema.yaml.new /tmp/schema.yaml

RUN mv /tmp/hedera-mirror-node.tar.gz /data/chart/
RUN mv /tmp/schema.yaml /data/schema.yaml

#RUN cp /data/schema.yaml /data-test/schema.yaml
