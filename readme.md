Configuration:

    seed_provider:
        - class_name: lt.nkts.cassandra.ConsulSeedProvider
          parameters:
              - consul_url: http://localhost:8500/v1/kv/service/cassandra/seeds

Build:

    mvn clean compile assembly:single
