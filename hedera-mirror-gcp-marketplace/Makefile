include ../crd.Makefile
include ../gcloud.Makefile
include ../var.Makefile
include ../images.Makefile

CHART_NAME := hedera-mirror
APP_ID ?= $(CHART_NAME)
TRACK ?= 0.1

IMPORTER_TAG ?= 0.10.1
GRPC_TAG ?= 0.10.1
REST_TAG ?= 0.10.1
LOKI_TAG ?= 0.36.1
POSTGRES_TAG ?= 3.1.0
PROMETHEUS_ADAPTER_TAG = 2.3.1
PROMETHEUS_OPERATOR_TAG = 8.13.2
TRAEFIK_TAG = 8.1.2

SOURCE_REGISTRY ?= marketplace.gcr.io/google
IMAGE_IMPORTER ?= $(SOURCE_REGISTRY)/hedera-mirror:$(IMPORTER_TAG)
IMAGE_GRPC ?= $(SOURCE_REGISTRY)/hedera-mirror/hedera-mirror-grpc:$(GRPC_TAG)
IMAGE_REST ?= $(SOURCE_REGISTRY)/hedera-mirror-rest:$(IMPORTER_REST)
IMAGE_LOKI ?= $(SOURCE_REGISTRY)/loki-stack:$(LOKI_TAG)
IMAGE_POSTGRES ?= $(SOURCE_REGISTRY)/postgresql-ha:$(POSTGRES_TAG)
IMAGE_PROMETHEUS_ADAPTER ?= $(SOURCE_REGISTRY)/prometheus-adapter:$(PROMETHEUS_ADAPTER_TAG)
IMAGE_PROMETHEUS_OPERATOR ?= $(SOURCE_REGISTRY)/prometheus-operator:$(PROMETHEUS_OPERATOR_TAG)
IMAGE_TRAEFIK ?= $(SOURCE_REGISTRY)/traefik:$(TRAEFIK_TAG)

# Main image
image-$(CHART_NAME) := $(call get_sha256,$(IMAGE_IMPORTER))

# List of images used in application
ADDITIONAL_IMAGES := hedera-mirror-grpc hedera-mirror-rest loki-stack postgresql-ha prometheus-adapter prometheus-operator traefik

# Additional images variable names should correspond with ADDITIONAL_IMAGES list
hedera-mirror-grpc := $(call get_sha256,$(IMAGE_GRPC))
hedera-mirror-rest := $(call get_sha256,$(IMAGE_REST))
loki-stack := $(call get_sha256,$(IMAGE_LOKI))
postgresql-ha := $(call get_sha256,$(IMAGE_POSTGRES))
prometheus-adapter := $(call get_sha256,$(IMAGE_PROMETHEUS_ADAPTER))
prometheus-operator := $(call get_sha256,$(IMAGE_PROMETHEUS_OPERATOR))
traefik := $(call get_sha256,$(IMAGE_TRAEFIK))

C2D_CONTAINER_RELEASE := $(call get_c2d_release,$(image-$(CHART_NAME)))

BUILD_ID := $(shell date --utc +%Y%m%d-%H%M%S)
RELEASE ?= $(C2D_CONTAINER_RELEASE)-$(BUILD_ID)
NAME ?= $(APP_ID)-1

# Additional variables
ifdef METRICS_EXPORTER_ENABLED
  METRICS_EXPORTER_ENABLED_FIELD = , "metrics.exporter.enabled": $(METRICS_EXPORTER_ENABLED)
endif

$(info ---- TRACK = $(TRACK))
$(info ---- SOURCE_REGISTRY = $(SOURCE_REGISTRY))

APP_PARAMETERS ?= { \
  "name": "$(NAME)", \
  "namespace": "$(NAMESPACE)" \
  $(METRICS_EXPORTER_ENABLED_FIELD) \
}

# c2d_deployer.Makefile provides the main targets for installing the application.
# It requires several APP_* variables defined above, and thus must be included after.
include ../c2d_deployer.Makefile

# Build tester image
app/build:: .build/$(CHART_NAME)/tester
