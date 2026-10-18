# auth-svc — Runbook

## Health check
`GET /actuator/health`

## Restart
`kubectl rollout restart deployment/auth-svc -n cpms-tenant`

## Logs
`kubectl logs -l app=auth-svc -n cpms-tenant --tail=200 -f`
