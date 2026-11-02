#!/bin/sh
set -eu
# The certificate is public; no private keys or database passwords enter the image.
: "${DB_CA_CERT_BASE64:?Set the database CA certificate}"
printf '%s' "$DB_CA_CERT_BASE64" | base64 -d > /tmp/credlearn-db-ca.pem
keytool -importcert -noprompt -alias credlearn-db -file /tmp/credlearn-db-ca.pem \
  -keystore /tmp/credlearn-db-trust.p12 -storetype PKCS12 -storepass changeit >/dev/null 2>&1
exec java -jar /app/app.jar
