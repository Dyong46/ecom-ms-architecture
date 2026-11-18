# Product search: SKU documents with SPU grouping

Status: Accepted. Date: 2026-11-18. Decision owner: project owner.

For the end-to-end walkthrough, see the [SPU/SKU Elasticsearch guide (Vietnamese)](product-search-guide.vi.md).

## Decision and scope

The owner selected an Elasticsearch document per SKU and search results grouped
by SPU: each result is one SKU that actually matches the request. This replaces
the earlier SPU-only projection. Product uses Spring Data Elasticsearch directly,
keeps after-commit synchronization, and uses explicit reindex for recovery. No
shared search module, Kafka, outbox, background worker or generic facade is added.

The index is now `products_v2`. The old `products` index is not read, modified or
deleted by this change. The new index is created lazily with explicit mappings.
PostgreSQL remains authoritative for checkout and all catalog business rules.

## SKU projection

`integration/search/SkuSearchDocument` contains:

| Field | Meaning / mapping |
| --- | --- |
| `skuId` | SKU UUID, keyword and Elasticsearch document `_id` |
| `spuId` | Owning Product UUID, keyword used for collapse |
| `sku` | Exact SKU code, keyword |
| `slug` | SPU slug, keyword |
| `skuTitle` | SPU name, full-text |
| `description` | SPU description, full-text |
| `skuPrice` | SKU price, scaled float with scaling factor 100 |
| `stockQuantity` | SKU stock, integer |
| `hasStock` | Whether this SKU has stock greater than zero |
| `attrs` | Nested array, containing only this SKU's option selections |

Each nested attribute stores `attrId` (Option UUID), `attrName`, `attrValueId`
(OptionValue UUID) and `attrValue`, all keywords. Price is a search projection;
checkout still obtains the authoritative price from PostgreSQL. No placeholder
brand/image/category or fabricated hotScore/saleCount values are indexed.

A Product without SKUs has no search documents and does not appear in results.
MapStruct mapping remains owned by Product. The four standalone catalog IDs and
business schema are described in [Product catalog design](product-catalog-design.md).

## Search API

```http
GET /api/v1/products/search?q=phone&page=1&size=20
GET /api/v1/products/search?q=DEMO-PHONE-01-01
```

Public GET retains the existing read-access policy. `q` is optional, maximum 200
characters; absent/blank means browse all matching SKUs before grouping. Nonblank
queries use AND token matching with AUTO fuzziness over SPU name/description,
plus an exact ASCII case-insensitive SKU-code clause. Exact SKU-code matches are
boosted. No autocomplete, substring SKU search, custom Vietnamese accent folding
or synonym analyzer is configured.

Attribute filters use indexed query parameters. For example, from a shell:

```sh
curl --get 'http://localhost:8050/api/v1/products/search' \
  --data-urlencode 'q=điện thoại' \
  --data-urlencode 'options[0].name=Màu sắc' \
  --data-urlencode 'options[0].value=Đen' \
  --data-urlencode 'options[1].name=Dung lượng' \
  --data-urlencode 'options[1].value=256GB' \
  --data-urlencode 'hasStock=true' \
  --data-urlencode 'page=1' \
  --data-urlencode 'size=20'
```

There are at most 20 filters. Each requires a nonblank name/value of at most 100
characters. Names/values match exactly, including case and accents, using keyword
fields. Filters are ANDed; multiple values for the same option are not interpreted
as OR. Each name/value pair must match the same nested attribute, and all selected
attributes must belong to the same root SKU document. Separate Black/128GB and
White/256GB SKUs therefore cannot satisfy Black/256GB.

`hasStock` is optional: true selects stocked SKUs, false selects sold-out SKUs,
and omission includes both. It applies before grouping, just like attribute filters.
There is no implicit business decision to hide sold-out SKUs.

## Results, ranking and pagination

The response remains `BaseResponse<PageResponse<...>>`, but content now consists of
`SkuSearchDocument` with the fields above. It no longer has the old SPU `id`/`name`
shape; consumers use `spuId`/`skuTitle` and `skuId` for the selected variant.

Search collapses on `spuId`, returning the best sorted matching SKU per Product.
Ranking is relevance descending, then `spuId` ascending, then `skuId` ascending for
stable ties. The representative is not necessarily the cheapest SKU or the one
with the most stock. No popularity ranking is introduced.

Pages start at 1, size defaults to 20 and is capped at 100. The requested result
window cannot exceed 10,000, preserving the existing offset-pagination limit.
`total_elements` counts matching **SPUs**, not SKU documents. The implementation
uses composite aggregation pages of 500 distinct SPU buckets and enumerates them
rather than using pre-collapse `hits.total` or an approximate cardinality count.
This requires additional Elasticsearch requests and work proportional to the
number of matching SPUs. Revisit count strategy for a much larger catalog.

Queries/count requests do not share a point-in-time snapshot; concurrent indexing
can change the visible result set while counting/paging. No deep-pagination or
snapshot-consistency guarantee is introduced. Search errors use the existing
HTTP error handling, without a PostgreSQL fallback.

## Synchronization and deletion

```text
Product transaction commits
  → ProductSearchChanged(spuId), in-process AFTER_COMMIT listener
  → read current Product + SKUs/options in a new read-only transaction
  → bulk upsert documents with _id = skuId
  → refresh the index for cleanup visibility
  → delete documents for this SPU whose SKU IDs are no longer present
```

The existing synchronous after-commit flow now runs for:

- Product creation and hard deletion.
- SKU addition and deletion.
- Purchases changing stock; one event per affected SPU in a purchase batch.
- Option/OptionValue cascade deletion; affected SPU IDs are collected before deleting
  joins, then published for synchronization after commit.

Deleted Products or Products with no SKUs cause all documents for that SPU to be
removed. Repeated synchronization updates the same `_id` values instead of adding
copies. Existing documents from removed SKUs are explicitly cleaned up. Bulk
failures propagate from Spring Data to the listener, which logs failure rather
than reporting full synchronization success. Cleanup timeouts/conflicts/partial
failures are also treated as synchronization failures.

The listener catches runtime indexing errors so an Elasticsearch failure does not
intentionally fail the already committed business operation. Requests, including
purchases, may wait for Elasticsearch calls and configured timeouts. Failed writes
are not durably retried. A process crash after commit or overlapping syncs can
still leave stale data; explicit reindex remains the recovery mechanism.

Creation of an unused Option/OptionValue does not affect existing SKU documents.
Future Product/SKU/option update APIs must publish the same event for affected
SPUs when indexed fields change. Direct SQL/imports bypass the event and require
reindex. The selected hard-cascade semantics can leave SKUs with fewer options;
synchronization reflects that state rather than repairing the catalog.

## Local setup and migration

```sh
docker compose --profile search up -d elasticsearch
```

The local image remains Elasticsearch 8.13.4, matching the client managed by
Spring Boot 3.3.3. See the
[Spring Data compatibility table](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/versions.html).
It is a local single-node container with a persistent volume, 512 MB JVM heap,
loopback port 9200 and authentication disabled. Config Server supplies
`ELASTICSEARCH_URIS` (default `http://localhost:9200`), `ELASTICSEARCH_USERNAME`
and `ELASTICSEARCH_PASSWORD` (default empty), with 2-second connection and
5-second socket timeouts. A remote secured cluster needs its HTTPS URI, runtime
credentials and trust configuration.

Existing PostgreSQL Product/Order schemas do not need resetting for this index
change. Rebuild/restart Product and populate the new index with the authenticated
maintenance operation:

```http
POST /api/v1/products/search/reindex
Authorization: Bearer <access-token>
```

The endpoint clears documents from `products_v2`, reads Products in batches of 200,
bulk-indexes their current SKUs, refreshes the index and returns the number of
**SKU documents** indexed. An empty catalog clears the index and returns zero.
The old `products` index may be removed separately when no old application uses it;
it is not needed for the new search.

Run one rebuild at a time during maintenance, pausing Product/SKU/option writes and
purchases and waiting for in-flight writes. Rebuild modifies the live index,
so search can temporarily show incomplete results. No distributed maintenance lock
or atomic alias cutover is provided. If a rebuild fails, fix the cause and rerun.
It does not recreate mappings; later incompatible mapping changes need a new
index or a separate migration plan.

## Verification

```sh
./services/product/mvnw -f pom.xml -pl services/product -am test --batch-mode
```

Tests exercise real Elasticsearch and PostgreSQL: per-SKU IDs/mapping, exact SKU
search, same-SKU nested filters, collapse and SPU totals across aggregation pages,
stock after purchase, creation/stock rollback, variant/SPU/option deletion cleanup,
repeated upserts, bulk failure recovery and multi-batch reindex.
