SELECT format('CREATE DATABASE %I', name)
FROM (VALUES ('product'), ('order'), ('payment')) AS required_databases(name)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = name)
\gexec
