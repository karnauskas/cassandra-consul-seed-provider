Configuration:

    seed_provider:
        - class_name: lt.nkts.cassandra.ConsulSeedProvider
          parameters:
              - consul_url: http://localhost:8500
              - consul_service: cassandra
              - use_kv: false
              - kv_prefix: cassandra/seeds

If use_kv is enabled, provider will look for entries with kv_prefix prefix/namespace
otherwise it will try to locate seed nodes from service discovery where service id is consul_service.

Set KV seed:

    curl -XPUT localhost:8500/v1/kv/cassandra/seeds/some-seed-server.example.com

Build:

    mvn clean compile assembly:single
