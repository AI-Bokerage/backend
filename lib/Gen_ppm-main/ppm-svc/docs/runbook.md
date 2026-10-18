# ppm-svc — Runbook

## Health check
`GET /actuator/health`

## Restart
`kubectl rollout restart deployment/ppm-svc -n cpms-tenant`

## Logs
`kubectl logs -l app=ppm-svc -n cpms-tenant --tail=200 -f`
