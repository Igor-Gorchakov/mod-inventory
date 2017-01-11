#!/usr/bin/env bash

inventory_root_address=${1:-http://localhost:9403/inventory}
tenant=${2:-demo_tenant}

curl -w '\n' -X POST -D - \
     -H "Content-type: multipart/form-data" \
     -H "X-Okapi-Tenant: ${tenant}" \
     -F upload=@./multiple-mods-records.xml \
     "${inventory_root_address}/ingest/mods"