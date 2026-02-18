# Prometheus App Targets

`app-targets.json` is environment-specific and is not tracked by git.

- For local use, create it from the example:
  - `cp monitoring/prometheus/targets/app-targets.example.json monitoring/prometheus/targets/app-targets.json`
- For remote deployment, `scripts/aws/deploy_remote_prometheus.sh` generates and uploads `app-targets.json`.
  - Priority 1: `APP_METRICS_TARGET` / `APP_METRICS_TARGETS`
  - Priority 2: SSM parameter (`/miyou/prod/APP_METRICS_TARGETS`)

## Remote deploy example

```bash
SSH_OPTS='-i /path/to/key.pem -o StrictHostKeyChecking=no' \
USE_SSM_TARGETS=true \
SSM_TARGETS_PARAM=/miyou/prod/APP_METRICS_TARGETS \
bash ./scripts/aws/deploy_remote_prometheus.sh ubuntu@<MONITORING_SERVER_IP>
```
