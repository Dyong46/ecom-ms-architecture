"""Exercise catalog validation and the actual HTTP importer against an isolated API fixture."""

from contextlib import redirect_stderr, redirect_stdout
import copy
import io
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import tempfile
from threading import Thread
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit
from uuid import uuid4

from import_products import DEFAULT_FILE, ImportFailure, ProductApi, import_catalog, load_catalog, main


class CatalogValidationTest(unittest.TestCase):
    def setUp(self):
        self.products = load_catalog(DEFAULT_FILE)

    def test_bundled_data_matches_requested_counts_and_ranges(self):
        self.assertEqual(len(self.products), 50)
        self.assertEqual({p["group"] for p in self.products},
                         {"Áo nam", "Áo nữ", "Điện thoại", "Laptop", "Bàn phím", "Màn hình"})
        variants = [v for product in self.products for v in product["variants"]]
        self.assertEqual(len(variants), 323)
        self.assertEqual({len(product["variants"]) for product in self.products}, set(range(3, 11)))
        self.assertEqual(len({v["sku"] for v in variants}), 323)
        self.assertEqual(min(v["stockQuantity"] for v in variants), 1)
        self.assertEqual(max(v["stockQuantity"] for v in variants), 100)

    def test_rejects_invalid_stock_price_duplicate_skus_and_combinations_before_http(self):
        cases = []
        for stock in (0, 101, 1.5, True):
            data = copy.deepcopy(self.products)
            data[0]["variants"][0]["stockQuantity"] = stock
            cases.append(data)
        for price in (-1, 10000000000, 1.123):
            data = copy.deepcopy(self.products)
            data[0]["variants"][0]["price"] = price
            cases.append(data)
        data = copy.deepcopy(self.products)
        data[1]["variants"][0]["sku"] = data[0]["variants"][0]["sku"]
        cases.append(data)
        data = copy.deepcopy(self.products)
        data[0]["variants"][1]["options"] = data[0]["variants"][0]["options"]
        cases.append(data)
        data = copy.deepcopy(self.products)
        data[0]["variants"][1]["options"].pop("Size")
        cases.append(data)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "invalid.json"
            for index, data in enumerate(cases):
                with self.subTest(case=index):
                    path.write_text(json.dumps({"products": data}), encoding="utf-8")
                    with patch("import_products.ProductApi") as api:
                        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                            self.assertEqual(main([str(path)]), 1)
                        api.assert_not_called()

    def test_rejects_duplicate_json_keys_instead_of_silently_discarding_a_selection(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "duplicate.json"
            path.write_text('{"products":[],"products":[]}', encoding="utf-8")
            with self.assertRaisesRegex(ImportFailure, "Duplicate JSON key"):
                load_catalog(path)

    def test_dry_run_needs_no_token_and_never_constructs_an_http_client(self):
        with patch.dict("os.environ", {}, clear=True), patch("import_products.ProductApi") as api:
            with redirect_stdout(io.StringIO()) as output:
                self.assertEqual(main(["--dry-run"]), 0)
            api.assert_not_called()
            self.assertIn("50 products, 323 SKUs", output.getvalue())


class ImportHttpTest(unittest.TestCase):
    def setUp(self):
        self.products = []
        self.options = []
        self.calls = []
        self.fail_slug = None
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def reply(self, data, status=200):
                body = json.dumps({"data": data}, ensure_ascii=False).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def authenticated(self):
                if self.headers.get("Authorization") != "Bearer test-only-token":
                    self.reply(None, 401)
                    return False
                return True

            def do_GET(self):
                fixture.calls.append(("GET", self.path, None))
                if not self.authenticated():
                    return
                parts = urlsplit(self.path)
                if parts.path == "/api/v1/products":
                    rows = fixture.products
                elif parts.path == "/api/v1/product-options":
                    rows = fixture.options
                else:
                    self.reply(None, 404)
                    return
                # A small server-side page cap ensures the importer must follow page_info.
                page = int(parse_qs(parts.query)["page"][0])
                size = 2
                self.reply({"content": rows[(page - 1) * size:page * size],
                            "page_info": {"is_last": page * size >= len(rows)}})

            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                fixture.calls.append(("POST", self.path, body))
                if not self.authenticated():
                    return
                identifier = str(uuid4())
                if self.path == "/api/v1/products":
                    if body["slug"] == fixture.fail_slug:
                        self.reply(None, 503)
                        return
                    if any(product["slug"] == body["slug"] for product in fixture.products):
                        self.reply(None, 409)
                        return
                    fixture.products.append({"id": identifier, **body})
                elif self.path == "/api/v1/product-options":
                    if any(option["name"] == body["name"] for option in fixture.options):
                        self.reply(None, 409)
                        return
                    fixture.options.append({"id": identifier, "name": body["name"], "values": []})
                elif self.path.startswith("/api/v1/product-options/") and self.path.endswith("/values"):
                    option_id = self.path.split("/")[-2]
                    option = next(option for option in fixture.options if option["id"] == option_id)
                    option["values"].append({"id": identifier, "value": body["value"], "synonyms": None})
                else:
                    self.reply(None, 404)
                    return
                self.reply(identifier)

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"
        self.api = ProductApi(self.base_url, "test-only-token", timeout=5)
        self.catalog = load_catalog(DEFAULT_FILE)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)

    def run_import(self, catalog=None, skip=False):
        with redirect_stdout(io.StringIO()):
            return import_catalog(self.api, self.catalog if catalog is None else catalog, skip)

    def test_imports_all_products_resolves_uuid_options_and_reuses_paginated_existing_options(self):
        color, black = str(uuid4()), str(uuid4())
        self.options.extend([
            {"id": str(uuid4()), "name": "Unused 1", "values": []},
            {"id": str(uuid4()), "name": "Unused 2", "values": []},
            {"id": color, "name": "Màu sắc", "values": [{"id": black, "value": "Đen"}]},
        ])
        self.assertEqual(self.run_import(), (50, 0))
        self.assertEqual(len(self.products), 50)
        self.assertEqual(sum(len(product["variants"]) for product in self.products), 323)
        self.assertEqual(sum(option["name"] == "Màu sắc" for option in self.options), 1)
        values = {value["id"]: (option["name"], value["value"])
                  for option in self.options for value in option["values"]}
        for original, imported in zip(self.catalog, self.products):
            self.assertNotIn("group", imported)
            for source, variant in zip(original["variants"], imported["variants"]):
                self.assertEqual(dict(values[value_id] for value_id in variant["optionValueIds"]), source["options"])
                self.assertNotIn("options", variant)
                self.assertIs(type(variant["stockQuantity"]), int)
        self.assertTrue(any("page=2" in path for method, path, body in self.calls if "product-options?" in path))

    def test_rerun_skips_whole_products_without_resetting_stock_or_creating_more_options(self):
        self.run_import()
        self.products[0]["variants"][0]["stockQuantity"] = 0  # Simulate stock consumed after import.
        self.calls.clear()
        self.assertEqual(self.run_import(skip=True), (0, 50))
        self.assertEqual(self.products[0]["variants"][0]["stockQuantity"], 0)
        self.assertFalse(any(method == "POST" for method, _, _ in self.calls))
        self.assertTrue(any("page=25" in path for _, path, _ in self.calls))

    def test_conflicts_are_found_before_any_writes(self):
        self.products.append({"slug": self.catalog[-1]["slug"], "variants": []})
        with self.assertRaisesRegex(ImportFailure, "Slug already exists"):
            self.run_import()
        self.assertFalse(any(method == "POST" for method, _, _ in self.calls))
        self.products[0]["slug"] = "some-other-product"
        self.products[0]["variants"] = [{"sku": self.catalog[-1]["variants"][0]["sku"]}]
        with self.assertRaisesRegex(ImportFailure, "SKU code already exists"):
            self.run_import(skip=True)
        self.assertFalse(any(method == "POST" for method, _, _ in self.calls))

    def test_failed_product_is_not_retried_and_partial_import_can_resume(self):
        self.fail_slug = self.catalog[1]["slug"]
        with self.assertRaisesRegex(ImportFailure, "HTTP 503"):
            self.run_import(self.catalog[:2])
        self.assertEqual(len(self.products), 1)
        failures = [body for method, path, body in self.calls if method == "POST"
                    and path == "/api/v1/products" and body["slug"] == self.fail_slug]
        self.assertEqual(len(failures), 1)
        self.fail_slug = None
        self.assertEqual(self.run_import(self.catalog[:2], skip=True), (1, 1))
        self.assertEqual(len(self.products), 2)

    def test_expired_token_stops_before_writes_and_is_not_printed(self):
        with patch.dict("os.environ", {"PRODUCT_API_TOKEN": "expired-secret-token"}):
            with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()) as errors:
                self.assertEqual(main([str(DEFAULT_FILE), "--base-url", self.base_url]), 1)
        self.assertIn("HTTP 401", errors.getvalue())
        self.assertNotIn("expired-secret-token", errors.getvalue())
        self.assertFalse(any(method == "POST" for method, _, _ in self.calls))


if __name__ == "__main__":
    unittest.main()
