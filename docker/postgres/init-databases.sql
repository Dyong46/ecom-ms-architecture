-- Create one login role for each service. Passwords are intentionally local-only.
DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'product_user') THEN
        CREATE ROLE product_user LOGIN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_user') THEN
        CREATE ROLE order_user LOGIN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_user') THEN
        CREATE ROLE payment_user LOGIN;
    END IF;
END
$$;

-- Keep local credentials deterministic when the bootstrap script is rerun.
ALTER ROLE product_user WITH LOGIN PASSWORD 'product_password';
ALTER ROLE order_user WITH LOGIN PASSWORD 'order_password';
ALTER ROLE payment_user WITH LOGIN PASSWORD 'payment_password';

-- CREATE DATABASE cannot run inside a transaction or DO block, so psql executes
-- each generated statement separately through \gexec.
SELECT format('CREATE DATABASE %I OWNER TO %I', database_name, owner_name)
FROM (
    VALUES
        ('product', 'product_user'),
        ('order', 'order_user'),
        ('payment', 'payment_user')
) AS required_databases(database_name, owner_name)
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = database_name
)
\gexec

-- Correct ownership for databases created by an older version of this script.
ALTER DATABASE product OWNER TO product_user;
ALTER DATABASE "order" OWNER TO order_user;
ALTER DATABASE payment OWNER TO payment_user;

-- PostgreSQL grants CONNECT to PUBLIC by default. Restrict each database to its
-- owning service role.
REVOKE ALL PRIVILEGES ON DATABASE product FROM PUBLIC;
REVOKE ALL PRIVILEGES ON DATABASE "order" FROM PUBLIC;
REVOKE ALL PRIVILEGES ON DATABASE payment FROM PUBLIC;

GRANT CONNECT ON DATABASE product TO product_user;
GRANT CONNECT ON DATABASE "order" TO order_user;
GRANT CONNECT ON DATABASE payment TO payment_user;

-- Product database
\connect product

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO product_user;
GRANT USAGE, CREATE ON SCHEMA public TO product_user;

-- Transfer objects created previously by the root development user.
SELECT format('ALTER TABLE %I.%I OWNER TO product_user', schemaname, tablename)
FROM pg_tables
WHERE schemaname = 'public'
\gexec

SELECT format('ALTER SEQUENCE %I.%I OWNER TO product_user', schemaname, sequencename)
FROM pg_sequences
WHERE schemaname = 'public'
\gexec

-- Order database
\connect order

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO order_user;
GRANT USAGE, CREATE ON SCHEMA public TO order_user;

SELECT format('ALTER TABLE %I.%I OWNER TO order_user', schemaname, tablename)
FROM pg_tables
WHERE schemaname = 'public'
\gexec

SELECT format('ALTER SEQUENCE %I.%I OWNER TO order_user', schemaname, sequencename)
FROM pg_sequences
WHERE schemaname = 'public'
\gexec

-- Payment database
\connect payment

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO payment_user;
GRANT USAGE, CREATE ON SCHEMA public TO payment_user;

SELECT format('ALTER TABLE %I.%I OWNER TO payment_user', schemaname, tablename)
FROM pg_tables
WHERE schemaname = 'public'
\gexec

SELECT format('ALTER SEQUENCE %I.%I OWNER TO payment_user', schemaname, sequencename)
FROM pg_sequences
WHERE schemaname = 'public'
\gexec
