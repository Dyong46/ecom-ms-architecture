FROM postgres:16-bookworm

# Install Python and Patroni with etcd3 support
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-dev \
    curl \
    && pip3 install --break-system-packages \
        patroni[etcd3] \
        psycopg2-binary \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create data directory for Patroni-managed PostgreSQL
RUN mkdir -p /data/patroni \
    && chown postgres:postgres /data/patroni \
    && chmod 700 /data/patroni

USER postgres

ENTRYPOINT ["patroni", "/etc/patroni/patroni.yml"]
