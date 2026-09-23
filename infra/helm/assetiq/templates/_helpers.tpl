{{/*
Chart name, overridable.
*/}}
{{- define "assetiq.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Fully qualified release name, capped at 63 chars (DNS label limit).
Workload templates append a "-backend"/"-web" suffix, so this is capped at 55
to leave room for the longest suffix without silently colliding.
*/}}
{{- define "assetiq.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 55 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 55 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 55 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "assetiq.backend.fullname" -}}
{{- printf "%s-backend" (include "assetiq.fullname" .) -}}
{{- end -}}

{{- define "assetiq.web.fullname" -}}
{{- printf "%s-web" (include "assetiq.fullname" .) -}}
{{- end -}}

{{- define "assetiq.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
The application version used for the app.kubernetes.io/version label.
Derived from the resolved image tag so the label always matches what is running.
*/}}
{{- define "assetiq.backend.tag" -}}
{{- default .Values.image.tag .Values.backend.image.tag -}}
{{- end -}}

{{- define "assetiq.web.tag" -}}
{{- default .Values.image.tag .Values.web.image.tag -}}
{{- end -}}

{{/*
Fully qualified image references. Fails loudly on an empty or `latest` tag —
`latest` is not permitted in any environment because it makes a rollout
non-reproducible and breaks rollback.
*/}}
{{- define "assetiq.image" -}}
{{- $registry := .root.Values.image.registry -}}
{{- $repo := .repository -}}
{{- $tag := .tag -}}
{{- if not $tag -}}
{{- fail "image tag is empty: set image.tag (CI should pass the git-describe tag)" -}}
{{- end -}}
{{- if eq $tag "latest" -}}
{{- fail "image tag 'latest' is not permitted: pass an immutable version tag" -}}
{{- end -}}
{{- if $registry -}}
{{- printf "%s/%s:%s" (trimSuffix "/" $registry) $repo $tag -}}
{{- else -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end -}}
{{- end -}}

{{/*
Common labels shared by every object in the release.
Call with a dict: (dict "root" $ "component" "backend" "version" "1.2.3")
*/}}
{{- define "assetiq.labels" -}}
helm.sh/chart: {{ include "assetiq.chart" .root }}
{{ include "assetiq.selectorLabels" . }}
app.kubernetes.io/version: {{ .version | default .root.Chart.AppVersion | quote }}
app.kubernetes.io/part-of: assetiq
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
{{- end -}}

{{/*
Selector labels — the immutable subset. Never add version/chart here: changing
them would make Deployment.spec.selector immutable-field updates fail.
*/}}
{{- define "assetiq.selectorLabels" -}}
app.kubernetes.io/name: {{ include "assetiq.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{/*
Selector matching EVERY pod in this release, used by the default-deny
NetworkPolicy.
*/}}
{{- define "assetiq.releaseSelectorLabels" -}}
app.kubernetes.io/name: {{ include "assetiq.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "assetiq.backend.serviceAccountName" -}}
{{- if .Values.backend.serviceAccount.create -}}
{{- default (include "assetiq.backend.fullname" .) .Values.backend.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.backend.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "assetiq.web.serviceAccountName" -}}
{{- if .Values.web.serviceAccount.create -}}
{{- default (include "assetiq.web.fullname" .) .Values.web.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.web.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
Name of the Secret holding backend credentials.
This is a hard gate: the chart never invents or generates secret material, so a
release without a Secret must fail at render time rather than start a pod that
falls back to the insecure in-code JWT default.
*/}}
{{- define "assetiq.backend.secretName" -}}
{{- if .Values.backend.existingSecret -}}
{{- .Values.backend.existingSecret -}}
{{- else if .Values.externalSecret.enabled -}}
{{- fail "backend.existingSecret is required even with externalSecret.enabled: it names the Secret the ExternalSecret must create" -}}
{{- else -}}
{{- fail "backend.existingSecret is required. Create a Secret (or enable externalSecret) holding at least spring-datasource-password, app-jwt-secret and app-data-encryption-key, then set backend.existingSecret to its name. This chart ships no secret values." -}}
{{- end -}}
{{- end -}}

{{/*
Prometheus scrape annotations for clusters without the Prometheus Operator.
*/}}
{{- define "assetiq.backend.podAnnotations" -}}
{{- with .Values.backend.podAnnotations -}}
{{ toYaml . }}
{{- end -}}
{{- end -}}
