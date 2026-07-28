{{/* Chart-version label. */}}
{{- define "librarius.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Labels of a component. Usage: {{ include "librarius.labels" (dict "ctx" . "component" "web") }}
*/}}
{{- define "librarius.labels" -}}
app: {{ .ctx.Release.Name }}-{{ .component }}
component: {{ .component }}
chart: {{ include "librarius.chart" .ctx }}
release: {{ .ctx.Release.Name }}
heritage: {{ .ctx.Release.Service }}
{{- end -}}

{{/* Selector of a component. */}}
{{- define "librarius.selector" -}}
app: {{ .ctx.Release.Name }}-{{ .component }}
release: {{ .ctx.Release.Name }}
{{- end -}}

{{/*
Name of the Secret holding the PostgreSQL password, consumed by postgres, the api
and Keycloak. Rendering fails when it is not set: the chart must never fall back to
a password committed to the repository.
*/}}
{{- define "librarius.postgres.secretName" -}}
{{- required "postgres.existingSecret is required: create the Secret then deploy with --set postgres.existingSecret=<name> (see docs/DEPLOYMENT.md)" .Values.postgres.existingSecret -}}
{{- end -}}

{{/* Name of the Secret holding the Keycloak admin password. Same rule. */}}
{{- define "librarius.keycloak.secretName" -}}
{{- required "keycloak.existingSecret is required: create the Secret then deploy with --set keycloak.existingSecret=<name> (see docs/DEPLOYMENT.md)" .Values.keycloak.existingSecret -}}
{{- end -}}
