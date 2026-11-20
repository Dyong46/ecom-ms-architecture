#!/usr/bin/env python3
"""Exercise real Order -> Product -> Payment HTTP calls against disposable PostgreSQL.

Requires Docker, Java 17, OpenSSL and packaged service jars. Customer/OAuth use a
local fixture. Fault proxies return 504 only AFTER a real downstream commit.
Never connects to the developer's service databases, Redis, Kafka or Elasticsearch.
"""
import base64
import concurrent.futures
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid

ROOT = Path(__file__).resolve().parents[1]


def command(*args, **kwargs):
    return subprocess.check_output(args, text=True, **kwargs).strip()


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def request(url, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = Request(url, data=data, headers={'Content-Type': 'application/json', **(headers or {})})
    try:
        with urlopen(req, timeout=20) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.loads(error.read() or b'{}')


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()


def main():
    processes = {}
    servers = []
    container = 'checkout-idempotency-' + uuid.uuid4().hex[:12]
    with tempfile.TemporaryDirectory(prefix='checkout-idempotency-') as temp:
        folder = Path(temp)
        try:
            command('docker', 'run', '-d', '--rm', '--name', container,
                    '-e', 'POSTGRES_PASSWORD=isolated-test-only', '-p', '127.0.0.1::5432', 'postgres:16-alpine')
            for _ in range(100):
                if subprocess.run(['docker', 'exec', container, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres'],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                    break
                time.sleep(.1)
            else:
                raise RuntimeError('Isolated PostgreSQL did not start')
            pg_port = command('docker', 'port', container, '5432/tcp').rsplit(':', 1)[1]

            def sql(database, query):
                return command('docker', 'exec', container, 'psql', '-U', 'postgres', '-d', database,
                               '-v', 'ON_ERROR_STOP=1', '-At', '-c', query)

            for name in ('product', 'payment', 'orders'):
                sql('postgres', 'create database ' + name)

            subprocess.run(['openssl', 'genrsa', '-out', str(folder / 'key.pem'), '2048'],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            modulus = command('openssl', 'rsa', '-in', str(folder / 'key.pem'), '-modulus', '-noout').split('=', 1)[1]
            n = b64(bytes.fromhex(modulus))
            ports = {name: free_port() for name in ('order', 'product', 'payment')}
            fixture_port = free_port()
            proxy_port = free_port()
            issuer = f'http://127.0.0.1:{fixture_port}/realms/test'
            token_input = (b64(json.dumps({'alg': 'RS256', 'kid': 'test'}).encode()) + '.' +
                           b64(json.dumps({'iss': issuer, 'sub': 'test-service', 'azp': 'ecom-services',
                                           'exp': int(time.time()) + 1800}).encode()))
            signature = subprocess.check_output(['openssl', 'dgst', '-sha256', '-sign', str(folder / 'key.pem')],
                                                input=token_input.encode())
            token = token_input + '.' + b64(signature)
            calls = []
            customer_calls = []
            faults = {'product': True, 'payment': True}

            class Fixture(BaseHTTPRequestHandler):
                def log_message(self, *args):
                    pass

                def respond(self, status, value):
                    data = json.dumps(value).encode()
                    self.send_response(status)
                    self.send_header('Content-Type', 'application/json')
                    self.send_header('Content-Length', str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)

                def do_GET(self):
                    if self.path.endswith('/certs'):
                        return self.respond(200, {'keys': [{'kty': 'RSA', 'kid': 'test', 'alg': 'RS256',
                                                          'use': 'sig', 'n': n, 'e': 'AQAB'}]})
                    if self.path == '/internal/customers/c1':
                        customer_calls.append(self.path)
                        return self.respond(200, {'data': {'id': 'c1', 'firstname': 'Ada',
                                                          'lastname': 'Lovelace', 'email': 'ada@example.test'},
                                                  'request_id': None})
                    self.respond(501, {})

                def do_POST(self):
                    self.rfile.read(int(self.headers.get('Content-Length', 0)))
                    self.respond(200, {'access_token': token, 'token_type': 'Bearer', 'expires_in': 1800})

            class FaultProxy(Fixture):
                def do_POST(self):
                    service, path = self.path.lstrip('/').split('/', 1)
                    body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
                    calls.append((service, self.headers.get('Idempotency-Key')))
                    headers = {'Content-Type': 'application/json', 'Idempotency-Key': self.headers['Idempotency-Key']}
                    if self.headers.get('Authorization'):
                        headers['Authorization'] = self.headers['Authorization']
                    upstream = Request(f'http://127.0.0.1:{ports[service]}/' + path, data=body, headers=headers)
                    try:
                        with urlopen(upstream, timeout=20) as response:
                            status, data = response.status, json.load(response)
                    except HTTPError as error:
                        return self.respond(error.code, json.loads(error.read()))
                    if status == 200 and faults[service]:
                        faults[service] = False
                        return self.respond(504, {'data': {'message': 'response lost after downstream commit'}})
                    self.respond(status, data)

            for handler, port in ((Fixture, fixture_port), (FaultProxy, proxy_port)):
                server = ThreadingHTTPServer(('127.0.0.1', port), handler)
                servers.append(server)
                threading.Thread(target=server.serve_forever, daemon=True).start()

            common = ['--spring.cloud.config.enabled=false', '--eureka.client.enabled=false',
                      '--spring.cloud.discovery.enabled=false', '--spring.jpa.hibernate.ddl-auto=validate',
                      '--spring.datasource.username=postgres', '--spring.datasource.password=isolated-test-only',
                      '--spring.datasource.driver-class-name=org.postgresql.Driver', '--spring.flyway.enabled=true',
                      '--server.address=127.0.0.1', '--management.tracing.enabled=false',
                      '--spring.kafka.admin.auto-create=false', '--spring.kafka.bootstrap-servers=127.0.0.1:1',
                      '--spring.kafka.producer.properties.max.block.ms=100',
                      '--spring.data.redis.host=127.0.0.1', '--spring.data.redis.port=1',
                      '--spring.data.redis.connect-timeout=100ms', '--spring.data.redis.timeout=100ms',
                      '--spring.elasticsearch.uris=http://127.0.0.1:1', '--spring.elasticsearch.connection-timeout=100ms',
                      '--spring.elasticsearch.socket-timeout=100ms',
                      '--spring.security.oauth2.resourceserver.jwt.issuer-uri=' + issuer,
                      '--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=' + issuer + '/certs']
            extra = {'order': [f'--application.config.product-url=http://127.0.0.1:{proxy_port}/product/internal/products',
                               f'--application.config.payment-url=http://127.0.0.1:{proxy_port}/payment/internal/payments',
                               f'--application.config.customer-url=http://127.0.0.1:{fixture_port}/internal/customers',
                               f'--integration-config.ecom.oauth-base-url=http://127.0.0.1:{fixture_port}',
                               '--integration-config.ecom.oauth-realm=test',
                               '--integration-config.ecom.oauth-username=ecom-services',
                               '--integration-config.ecom.oauth-password=isolated-test-only']}

            def start(name):
                jars = list((ROOT / 'services' / name / 'target').glob(name + '-*.jar'))
                if len(jars) != 1:
                    raise RuntimeError(f'Package {name} first: expected one executable jar')
                database = 'orders' if name == 'order' else name
                args = ['java', '-Xmx256m', '-jar', str(jars[0]), *common,
                        f'--server.port={ports[name]}',
                        f'--spring.datasource.url=jdbc:postgresql://127.0.0.1:{pg_port}/{database}', *extra.get(name, [])]
                log = open(folder / (name + '.log'), 'a')
                process = subprocess.Popen(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
                log.close()
                processes[name] = process
                endpoint = {'order': '/api/v1/orders', 'product': '/api/v1/products', 'payment': '/api/v1/payments'}[name]
                for _ in range(300):
                    if process.poll() is not None:
                        raise RuntimeError(f'{name} exited; see {folder / (name + ".log")}')
                    try:
                        if request(f'http://127.0.0.1:{ports[name]}' + endpoint)[0] == 200:
                            return
                    except (OSError, URLError):
                        pass
                    time.sleep(.1)
                raise RuntimeError(f'{name} startup timed out')

            with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
                list(executor.map(start, ('product', 'payment', 'order')))
            product_id, sku_id = str(uuid.uuid4()), str(uuid.uuid4())
            sql('product', f"insert into product(id,name,slug,created_at,updated_at) values ('{product_id}','Original Phone','phone',now(),now()); "
                           f"insert into product_variant(id,sku,price,stock_quantity,product_id,created_at,updated_at) "
                           f"values ('{sku_id}','PHONE',25,10,'{product_id}',now(),now())")
            body = {'reference': 'IDEMPOTENCY-TEST', 'paymentMethod': 'PAYPAL', 'customerId': 'c1',
                    'recipientFullName': 'Other Recipient', 'recipientPhone': '+84901234567',
                    'shippingAddress': {'addressLine1': '12 Main', 'ward': 'Ward', 'city': 'City', 'countryCode': 'VN'},
                    'products': [{'skuId': sku_id, 'quantity': 2}]}
            key = str(uuid.uuid4())
            url = f'http://127.0.0.1:{ports["order"]}/api/v1/orders'
            headers = {'Idempotency-Key': key}
            assert request(url, body)[0] == 400, 'Idempotency-Key must be required'
            assert request(url, body, {'Idempotency-Key': 'bad key'})[0] == 400
            assert not calls, 'Invalid keys must not call downstream services'
            assert request(url, body, headers)[0] >= 500, 'Expected lost Product response'
            assert sql('product', f"select stock_quantity from product_variant where id='{sku_id}'") == '8'
            sql('product', f"update product_variant set price=99 where id='{sku_id}'")
            assert request(url, body, headers)[0] >= 500, 'Expected lost Payment response'
            assert sql('payment', 'select count(*) from payment') == '1'
            assert sql('orders', 'select creation_status from customer_order') == 'PROCESSING'
            assert sql('orders', 'select total_amount from customer_order') == '50.00'
            before = len(customer_calls)
            processes['order'].terminate()
            processes['order'].wait(timeout=15)
            start('order')  # Same DB, new JVM: recovery cannot depend on memory.
            status, response = request(url, body, headers)
            assert status == 200, response
            order_id = response['data']
            assert request(url, body, headers)[1]['data'] == order_id
            assert len(customer_calls) == before, 'Recovery must reuse saved buyer snapshot'
            assert sql('orders', 'select creation_status from customer_order') == 'COMPLETED'
            assert sql('orders', 'select count(*) from customer_order') == '1'
            assert sql('payment', 'select count(*) from payment') == '1'
            assert sql('product', f"select stock_quantity from product_variant where id='{sku_id}'") == '8'
            assert sql('orders', 'select unit_price from order_line') == '25.00'
            for service in ('product', 'payment'):
                keys = [value for member, value in calls if member == service]
                assert len(keys) == 2 and len(set(keys)) == 1, (service, keys)
            previous_calls = len(calls)
            assert request(url, {**body, 'recipientFullName': 'Changed'}, headers)[0] == 409
            assert request(url, body, {'Idempotency-Key': str(uuid.uuid4())})[0] == 409
            assert len(calls) == previous_calls, 'Conflicts must not call downstream services'
            print('PASS: Product and Payment commits survive lost responses; Order resumes after JVM restart.')
            print('PASS: one order, one payment, stock decremented once, original price snapshot preserved.')
            print('PASS: payload mismatch and reference reuse return 409 before downstream calls.')
        finally:
            for process in processes.values():
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
            for server in servers:
                server.shutdown()
                server.server_close()
            subprocess.run(['docker', 'rm', '-f', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
