FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm AS build

## pull in chart, swap values file and archive chart
RUN echo $(ls .)
ADD charts/hedera-mirror /tmp/chart
RUN mv /tmp/chart/values-gcp-marketplace.yaml /tmp/chart/values.yaml
RUN cd /tmp && tar -czvf /tmp/hedera-mirror.tar.gz chart

##ADD apptest/deployer/hedera-mirror /tmp/test/chart
##RUN cd /tmp/test \
##    && tar -czvf /tmp/test/hedera-mirror.tar.gz chart/

ADD hedera-mirror-gcp-marketplace/schema.yaml /tmp/schema.yaml

ARG REGISTRY
ARG TAG

RUN cat /tmp/schema.yaml \
    | env -i "REGISTRY=$REGISTRY" "TAG=$TAG" envsubst \
    > /tmp/schema.yaml.new \
    && mv /tmp/schema.yaml.new /tmp/schema.yaml

#ADD apptest/deployer/schema.yaml /tmp/apptest/schema.yaml
#RUN cat /tmp/apptest/schema.yaml \
#    | env -i "REGISTRY=$REGISTRY" "TAG=$TAG" envsubst \
#    > /tmp/apptest/schema.yaml.new \
#    && mv /tmp/apptest/schema.yaml.new /tmp/apptest/schema.yaml

RUN mv /tmp/hedera-mirror.tar.gz /data/chart/
RUN mv /tmp/schema.yaml /data/schema.yaml
