FROM postgres:15

# Install Python and pip, then install Patroni with etcd3 support
RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 \
        python3-venv \
        curl \
        jq \
    && python3 -m venv /opt/patroni-env \
    && /opt/patroni-env/bin/pip install --no-cache-dir "patroni[etcd3]" \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="/opt/patroni-env/bin:${PATH}"

# Create data directory managed by Patroni (not the default postgres initdb path)
RUN mkdir -p /data/patroni \
    && chown postgres:postgres /data/patroni \
    && chmod 700 /data/patroni

USER postgres

CMD ["patroni", "/etc/patroni/patroni.yml"]
