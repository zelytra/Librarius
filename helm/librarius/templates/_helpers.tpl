{{/* Libellé chart-version. */}}
{{- define "librarius.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Labels d'un composant. Usage : {{ include "librarius.labels" (dict "ctx" . "component" "web") }}
*/}}
{{- define "librarius.labels" -}}
app: {{ .ctx.Release.Name }}-{{ .component }}
component: {{ .component }}
chart: {{ include "librarius.chart" .ctx }}
release: {{ .ctx.Release.Name }}
heritage: {{ .ctx.Release.Service }}
{{- end -}}

{{/* Sélecteur d'un composant. */}}
{{- define "librarius.selector" -}}
app: {{ .ctx.Release.Name }}-{{ .component }}
release: {{ .ctx.Release.Name }}
{{- end -}}
