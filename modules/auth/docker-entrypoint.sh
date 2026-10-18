#!/bin/sh
# Lets a platform without file mounts (Railway) pass the JWT signing keypair as base64 variables:
# JWT_PRIVATE_KEY_B64 / JWT_PUBLIC_KEY_B64 → files the auth service reads. Without them, the
# key paths from the environment (or the image's dev keys) apply unchanged.
set -e
if [ -n "$JWT_PRIVATE_KEY_B64" ] && [ -n "$JWT_PUBLIC_KEY_B64" ]; then
  mkdir -p /tmp/jwt && chmod 700 /tmp/jwt
  echo "$JWT_PRIVATE_KEY_B64" | base64 -d > /tmp/jwt/private.pem
  echo "$JWT_PUBLIC_KEY_B64" | base64 -d > /tmp/jwt/public.pem
  # An env var for one list entry replaces jwt.keys from application.yml entirely, so the entry
  # needs its kid (= jwt.active-kid) as well as both paths.
  export JWT_KEYS_0_KID=auth-key-v1
  export JWT_KEYS_0_PRIVATEKEYPATH=file:/tmp/jwt/private.pem
  export JWT_KEYS_0_PUBLICKEYPATH=file:/tmp/jwt/public.pem
  # The starter's legacy single-key beans (jwt.rsa.*) must hold the same pair, not the image's dev keys.
  export JWT_RSA_PRIVATEKEYPATH=file:/tmp/jwt/private.pem
  export JWT_RSA_PUBLICKEYPATH=file:/tmp/jwt/public.pem
fi
exec java -jar /app/app.jar "$@"
