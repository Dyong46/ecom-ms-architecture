#!/bin/bash
# post_init hook – runs once on the primary after the cluster is first bootstrapped.
# Creates the application databases used by the microservices.

set -e

PGHOST="${PATRONI_POSTGRESQL_CONNECT_ADDRESS:-localhost}"
PGUSER="postgres"

psql -U "$PGUSER" -c "CREATE DATABASE orderdb;" 2>/dev/null || echo "orderdb already exists"
psql -U "$PGUSER" -c "CREATE DATABASE paymentdb;" 2>/dev/null || echo "paymentdb already exists"
psql -U "$PGUSER" -c "CREATE DATABASE productdb;" 2>/dev/null || echo "productdb already exists"

echo "Application databases initialized successfully."
