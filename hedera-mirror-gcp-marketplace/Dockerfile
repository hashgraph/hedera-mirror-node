FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm AS build

## pull in chart, swap values file and archive chart
COPY chart /tmp/chart
#RUN  apt update \
#    && pip install yq
#RUN yq m -x /tmp/chart/values.yaml /tmp/chart/values-marketplace.yaml
RUN cat /tmp/chart/values.yaml
RUN mv /tmp/chart/values-marketplace.yaml /tmp/chart/values.yaml
RUN cd /tmp && tar -czvf /tmp/hedera-mirror-node.tar.gz chart/

COPY schema.yaml /tmp/schema.yaml

ARG tag

RUN cat /tmp/schema.yaml \
    | env -i "TAG=$tag" envsubst \
    > /tmp/schema.yaml.new \
    && mv /tmp/schema.yaml.new /tmp/schema.yaml

RUN mv /tmp/hedera-mirror-node.tar.gz /data/chart/
RUN mv /tmp/schema.yaml /data/schema.yaml

#RUN cp /data/schema.yaml /data-test/schema.yaml
