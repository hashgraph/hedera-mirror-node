{{/* vim: set filetype=mustache: */}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "hedera-mirror-grpc.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Database host
*/}}
{{- define "hedera-mirror-grpc.db-host" -}}
{{- $host := .Values.config.hedera.mirror.grpc.db.host | default "" -}}
{{- if .Values.global.db -}}
{{- $host = .Values.global.db.host }}
{{- end -}}
{{- replace "RELEASE-NAME" .Release.Name $host -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hedera-mirror-grpc.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "hedera-mirror-grpc.labels" -}}
{{ include "hedera-mirror-grpc.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: hedera-mirror-node
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ include "hedera-mirror-grpc.chart" . }}
{{- if .Values.labels }}
{{ toYaml .Values.labels }}
{{- end }}
{{- end -}}

{{/*
Expand the name of the chart.
*/}}
{{- define "hedera-mirror-grpc.name" -}}
{{- if .Values.global.useReleaseForNameLabel -}}
{{- .Release.Name -}}
{{- else -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/*
Namespace
*/}}
{{- define "hedera-mirror-grpc.namespace" -}}
{{- default .Release.Namespace .Values.global.namespaceOverride -}}
{{- end -}}

{{/*
Redis host
*/}}
{{- define "hedera-mirror-grpc.redis-host" -}}
{{- $host := "RELEASE-NAME-redis" -}}
{{- if .Values.global.redis.host -}}
{{- $host = .Values.global.redis.host -}}
{{- else if .Values.config.spring }}
{{- $host = .Values.config.spring.redis.host -}}
{{- end -}}
{{- replace "RELEASE-NAME" .Release.Name $host -}}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "hedera-mirror-grpc.selectorLabels" -}}
app.kubernetes.io/component: grpc
app.kubernetes.io/name: {{ include "hedera-mirror-grpc.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "hedera-mirror-grpc.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "hedera-mirror-grpc.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}
