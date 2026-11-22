#!/usr/bin/env python3
"""Import a JSON catalog through Product APIs using only the Python standard library."""

import argparse
from collections import Counter
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import UUID


DEFAULT_FILE = Path(__file__).resolve().parent / "data" / "products.json"


class ImportFailure(Exception):
    """A validation/API failure that should stop the import without retries."""


def require_text(value, label, maximum):
    if not isinstance(value, str) or not value.strip() or value != value.strip() or len(value) > maximum:
        raise ImportFailure(f"{label}: expected nonblank text, no surrounding spaces, max {maximum} characters")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ImportFailure(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_constant(value):
    raise ImportFailure(f"Invalid JSON number: {value}")


def load_catalog(path):
    try:
        document = json.loads(Path(path).read_text(encoding="utf-8"),
                              object_pairs_hook=unique_object, parse_constant=reject_constant)
    except (OSError, ValueError) as error:
        raise ImportFailure(f"Cannot read JSON file {path}: {error}") from error
    if not isinstance(document, dict) or not isinstance(document.get("products"), list) or not document["products"]:
        raise ImportFailure("JSON must contain a nonempty 'products' array")
    products = document["products"]
    slugs, skus = set(), set()
    for number, product in enumerate(products, 1):
        label = f"Product #{number}"
        if not isinstance(product, dict):
            raise ImportFailure(f"{label}: expected an object")
        require_text(product.get("name"), f"{label}.name", 255)
        require_text(product.get("slug"), f"{label}.slug", 255)
        if product["slug"] in slugs:
            raise ImportFailure(f"Duplicate slug: {product['slug']}")
        slugs.add(product["slug"])
        if product.get("description") is not None and not isinstance(product["description"], str):
            raise ImportFailure(f"{label}.description must be text or null")
        if "group" in product:
            require_text(product["group"], f"{label}.group", 100)
        variants = product.get("variants")
        if not isinstance(variants, list) or not 3 <= len(variants) <= 10:
            raise ImportFailure(f"{label}: expected 3-10 variants")
        combinations = set()
        axes = None
        for position, variant in enumerate(variants, 1):
            where = f"{label}, variant #{position}"
            if not isinstance(variant, dict):
                raise ImportFailure(f"{where}: expected an object")
            require_text(variant.get("sku"), f"{where}.sku", 255)
            if variant["sku"] in skus:
                raise ImportFailure(f"Duplicate SKU code: {variant['sku']}")
            skus.add(variant["sku"])
            price = variant.get("price")
            if type(price) not in (int, float):
                raise ImportFailure(f"{where}.price must be a JSON number")
            try:
                decimal = Decimal(str(price))
                valid = (decimal.is_finite() and Decimal("0") <= decimal <= Decimal("9999999999.99")
                         and decimal.as_tuple().exponent >= -2)
            except InvalidOperation:
                valid = False
            if not valid:
                raise ImportFailure(f"{where}.price must fit nonnegative decimal(12,2)")
            if type(variant.get("stockQuantity")) is not int or not 1 <= variant["stockQuantity"] <= 100:
                raise ImportFailure(f"{where}.stockQuantity must be an integer from 1 to 100")
            options = variant.get("options")
            if not isinstance(options, dict) or not options:
                raise ImportFailure(f"{where}.options must map option names to value names")
            for name, value in options.items():
                require_text(name, f"{where} option name", 100)
                require_text(value, f"{where} option value", 100)
            if axes is None:
                axes = set(options)
            if set(options) != axes:
                raise ImportFailure(f"{label}: all variants must select the same option axes")
            combination = tuple(sorted(options.items()))
            if combination in combinations:
                raise ImportFailure(f"{where}: duplicate option combination")
            combinations.add(combination)
    return products


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        # Do not forward the bearer token to a redirect destination.
        return None


class ProductApi:
    def __init__(self, base_url, token, timeout=30):
        parts = urlsplit(base_url)
        if (parts.scheme not in ("http", "https") or not parts.netloc or parts.username is not None
                or parts.query or parts.fragment):
            raise ImportFailure("Base URL must be an HTTP(S) URL without credentials, query or fragment")
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeout = timeout
        self.opener = build_opener(NoRedirects())

    def request(self, method, path, body=None):
        headers = {"Authorization": f"Bearer {self.token}", "Accept": "application/json"}
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body, ensure_ascii=False, allow_nan=False).encode("utf-8")
        request = Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                envelope = json.load(response)
        except HTTPError as error:
            error.close()
            hint = " Check the access token and permissions." if error.code in (401, 403) else ""
            raise ImportFailure(f"HTTP {error.code} on {method} {path}.{hint} No request was retried.") from error
        except (URLError, TimeoutError, OSError) as error:
            raise ImportFailure(f"Connection failed on {method} {path}. A write may have committed; "
                                "check the server before rerunning with --skip-existing.") from error
        except ValueError as error:
            raise ImportFailure(f"Invalid JSON response from {method} {path}") from error
        if not isinstance(envelope, dict) or "data" not in envelope:
            raise ImportFailure(f"Expected a BaseResponse data envelope from {method} {path}")
        return envelope["data"]

    def fetch_all(self, path):
        result = []
        page = 1
        while True:
            data = self.request("GET", path + "?" + urlencode({"page": page, "size": 100}))
            if (not isinstance(data, dict) or not isinstance(data.get("content"), list)
                    or not isinstance(data.get("page_info"), dict)
                    or type(data["page_info"].get("is_last")) is not bool):
                raise ImportFailure(f"Unexpected page response from {path}")
            result.extend(data["content"])
            if data["page_info"]["is_last"]:
                return result
            if not data["content"]:
                raise ImportFailure(f"Empty non-final page from {path}")
            page += 1


def require_uuid(value):
    try:
        if not isinstance(value, str):
            raise ValueError()
        UUID(value)
    except ValueError as error:
        raise ImportFailure("Expected a UUID string ID from Product API") from error
    return value


def import_catalog(api, products, skip_existing=False):
    existing_products = api.fetch_all("/api/v1/products")
    existing_slugs = {product["slug"] for product in existing_products}
    existing_skus = {variant["sku"] for product in existing_products for variant in product["variants"]}
    to_create = []
    skipped = 0
    # Check all known collisions before creating any option/value/product.
    for product in products:
        if product["slug"] in existing_slugs:
            if not skip_existing:
                raise ImportFailure(f"Slug already exists: {product['slug']}. Use --skip-existing to leave it unchanged.")
            skipped += 1
            continue
        collisions = existing_skus.intersection(variant["sku"] for variant in product["variants"])
        if collisions:
            raise ImportFailure(f"SKU code already exists on another product: {sorted(collisions)[0]}")
        to_create.append(product)
    if not to_create:
        print(f"Done: created=0, skipped={skipped}; existing products were not changed.")
        return 0, skipped
    options = {}
    for option in api.fetch_all("/api/v1/product-options"):
        options[option["name"]] = {
            "id": require_uuid(option["id"]),
            "values": {value["value"]: require_uuid(value["id"]) for value in option["values"]},
        }
    created = 0
    for product in to_create:
        variants = []
        for variant in product["variants"]:
            selected = []
            for name, value in variant["options"].items():
                if name not in options:
                    option_id = require_uuid(api.request("POST", "/api/v1/product-options", {"name": name}))
                    options[name] = {"id": option_id, "values": {}}
                option = options[name]
                if value not in option["values"]:
                    path = f"/api/v1/product-options/{option['id']}/values"
                    option["values"][value] = require_uuid(api.request("POST", path, {"value": value}))
                selected.append(option["values"][value])
            variants.append({"sku": variant["sku"], "price": variant["price"],
                             "stockQuantity": variant["stockQuantity"], "optionValueIds": selected})
        payload = {"name": product["name"], "slug": product["slug"],
                   "description": product.get("description"), "variants": variants}
        product_id = require_uuid(api.request("POST", "/api/v1/products", payload))
        created += 1
        print(f"[{created}/{len(to_create)}] Created {product['slug']}: {product_id} ({len(variants)} SKUs)", flush=True)
    print(f"Done: created={created}, skipped={skipped}.")
    return created, skipped


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path, nargs="?", default=DEFAULT_FILE, help="JSON file (default: bundled products.json)")
    parser.add_argument("--base-url", default="http://localhost:8050", help="Product service or Gateway origin")
    parser.add_argument("--dry-run", action="store_true", help="Validate and summarize JSON without HTTP requests")
    parser.add_argument("--skip-existing", action="store_true", help="Skip entire products with an existing slug; do not update them")
    parser.add_argument("--timeout", type=float, default=30, help="HTTP timeout in seconds")
    args = parser.parse_args(argv)
    try:
        if args.timeout <= 0 or args.timeout == float("inf") or args.timeout != args.timeout:
            raise ImportFailure("Timeout must be a finite positive number")
        products = load_catalog(args.file)
        variants = [variant for product in products for variant in product["variants"]]
        stock = [variant["stockQuantity"] for variant in variants]
        print(f"Validated: {len(products)} products, {len(variants)} SKUs, stock={min(stock)}..{max(stock)}.")
        for group, count in sorted(Counter(product.get("group", "Other") for product in products).items()):
            print(f"  {group}: {count}")
        if args.dry_run:
            print("Dry run: no HTTP requests or writes performed.")
            return 0
        token = os.environ.get("PRODUCT_API_TOKEN", "").strip()
        if not token or "\n" in token or "\r" in token:
            raise ImportFailure("Set PRODUCT_API_TOKEN to a valid Keycloak access token before importing")
        api = ProductApi(args.base_url, token, args.timeout)
        import_catalog(api, products, args.skip_existing)
        return 0
    except (ImportFailure, KeyError, TypeError) as error:
        print(f"Import stopped: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("Import interrupted. Completed writes remain; rerun with --skip-existing to resume.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
