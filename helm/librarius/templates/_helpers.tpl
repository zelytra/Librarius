{{/* Nom complet de la release. */}}
{{- define "librarius.fullname" -}}
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

{{/* Libellé chart-version. */}}
{{- define "librarius.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Labels communs. */}}
{{- define "librarius.labels" -}}
app: {{ include "librarius.fullname" . }}
chart: {{ include "librarius.chart" . }}
heritage: {{ .Release.Service }}
release: {{ .Release.Name }}
{{- end -}}
