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

{{/*
Name of the Secret holding the api's Keycloak service-account secret (account deletion,
#73): the one given, or the one the chart generates (templates/keycloak-setup.yaml).
*/}}
{{- define "librarius.apiAdmin.secretName" -}}
{{- .Values.api.accountDeletion.existingSecret | default (printf "%s-keycloak-api-admin" .Release.Name) -}}
{{- end -}}

{{/* Name of the Secret holding the Keycloak admin password. Same rule. */}}
{{- define "librarius.keycloak.secretName" -}}
{{- required "keycloak.existingSecret is required: create the Secret then deploy with --set keycloak.existingSecret=<name> (see docs/DEPLOYMENT.md)" .Values.keycloak.existingSecret -}}
{{- end -}}

{{/*
Name of the Secret holding the object-storage credentials and the backup
encryption passphrase. Only reached when backup.enabled is true, so the chart
still renders untouched for a deployment that has no backup destination yet.
*/}}
{{- define "librarius.backup.secretName" -}}
{{- required "backup.existingSecret is required when backup.enabled is true: create the Secret then deploy with --set backup.existingSecret=<name> (see docs/DEPLOYMENT.md)" .Values.backup.existingSecret -}}
{{- end -}}

{{/*
Whether an identity provider (#177) is on: forced by `enabled`, or because its Secret
exists in the namespace. `lookup` sees nothing under `helm template`, hence the flag.
Call with (dict "ctx" $ "idp" "google").
*/}}
{{- define "librarius.idp.enabled" -}}
{{- $idp := index .ctx.Values.keycloak.identityProviders .idp -}}
{{- if or $idp.enabled (lookup "v1" "Secret" .ctx.Release.Namespace $idp.secretName) -}}true{{- end -}}
{{- end -}}

{{/*
A container's hardening (#582): no privilege escalation, every capability dropped but the ones
an image cannot start without — pass them as (dict "add" (list "CHOWN" …)), or an empty dict.
*/}}
{{- define "librarius.containerHardening" -}}
allowPrivilegeEscalation: false
capabilities:
  drop: [ALL]
  {{- with .add }}
  add: {{ toJson . }}
  {{- end }}
{{- end -}}
