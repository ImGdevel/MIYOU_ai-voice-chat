#!/usr/bin/env bash
set -euo pipefail

docker run --rm \
  -v /opt/app/miyou/deploy/certbot/conf:/etc/letsencrypt \
  -v /opt/app/miyou/deploy/certbot/www:/var/www/certbot \
  certbot/certbot renew --webroot -w /var/www/certbot --quiet

docker exec miyou-nginx nginx -s reload
