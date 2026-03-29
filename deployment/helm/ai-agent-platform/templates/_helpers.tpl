{{/*
Expand the name of the chart.
*/}}
{{- define "ai-agent-platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "ai-agent-platform.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "ai-agent-platform.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "ai-agent-platform.labels" -}}
helm.sh/chart: {{ include "ai-agent-platform.chart" . }}
app.kubernetes.io/part-of: ai-agent-platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
{{- end }}

{{/*
Selector labels for a given service.
Usage: {{ include "ai-agent-platform.selectorLabels" (dict "name" "api-gateway") }}
*/}}
{{- define "ai-agent-platform.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
{{- end }}

{{/*
All labels for a given service.
Usage: {{ include "ai-agent-platform.serviceLabels" (dict "name" "api-gateway" "context" $) }}
*/}}
{{- define "ai-agent-platform.serviceLabels" -}}
{{ include "ai-agent-platform.selectorLabels" (dict "name" .name) }}
{{ include "ai-agent-platform.labels" .context }}
{{- end }}

{{/*
Create the image name with optional registry prefix.
Usage: {{ include "ai-agent-platform.image" (dict "repository" .Values.gateway.image.repository "tag" .Values.gateway.image.tag "global" .Values.global) }}
*/}}
{{- define "ai-agent-platform.image" -}}
{{- if .global.imageRegistry -}}
{{- printf "%s/%s:%s" .global.imageRegistry .repository .tag -}}
{{- else -}}
{{- printf "%s:%s" .repository .tag -}}
{{- end -}}
{{- end }}

{{/*
Standard pod security context.
*/}}
{{- define "ai-agent-platform.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: {{ .Values.securityContext.runAsUser }}
runAsGroup: {{ .Values.securityContext.runAsGroup }}
fsGroup: {{ .Values.securityContext.fsGroup }}
{{- end }}

{{/*
Standard container security context.
*/}}
{{- define "ai-agent-platform.containerSecurityContext" -}}
allowPrivilegeEscalation: {{ .Values.containerSecurityContext.allowPrivilegeEscalation }}
readOnlyRootFilesystem: {{ .Values.containerSecurityContext.readOnlyRootFilesystem }}
capabilities:
  drop:
  {{- range .Values.containerSecurityContext.capabilities.drop }}
    - {{ . }}
  {{- end }}
{{- end }}

{{/*
Standard Prometheus annotations.
Usage: {{ include "ai-agent-platform.prometheusAnnotations" (dict "port" "8080") }}
*/}}
{{- define "ai-agent-platform.prometheusAnnotations" -}}
prometheus.io/scrape: "true"
prometheus.io/port: {{ .port | quote }}
prometheus.io/path: "/actuator/prometheus"
{{- end }}

{{/*
Standard liveness probe for Spring Boot services.
*/}}
{{- define "ai-agent-platform.livenessProbe" -}}
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: http
  initialDelaySeconds: 60
  periodSeconds: 15
  timeoutSeconds: 5
  failureThreshold: 3
{{- end }}

{{/*
Standard readiness probe for Spring Boot services.
*/}}
{{- define "ai-agent-platform.readinessProbe" -}}
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: http
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
{{- end }}

{{/*
Standard startup probe for Spring Boot services.
*/}}
{{- define "ai-agent-platform.startupProbe" -}}
startupProbe:
  httpGet:
    path: /actuator/health
    port: http
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 30
{{- end }}

{{/*
Standard pod anti-affinity rule.
Usage: {{ include "ai-agent-platform.podAntiAffinity" (dict "name" "api-gateway") }}
*/}}
{{- define "ai-agent-platform.podAntiAffinity" -}}
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
              - key: app.kubernetes.io/name
                operator: In
                values:
                  - {{ .name }}
          topologyKey: kubernetes.io/hostname
{{- end }}

{{/*
HPA scale behavior defaults.
*/}}
{{- define "ai-agent-platform.hpaBehavior" -}}
behavior:
  scaleDown:
    stabilizationWindowSeconds: 300
    policies:
      - type: Pods
        value: 1
        periodSeconds: 60
  scaleUp:
    stabilizationWindowSeconds: 30
    policies:
      - type: Pods
        value: 2
        periodSeconds: 60
{{- end }}
