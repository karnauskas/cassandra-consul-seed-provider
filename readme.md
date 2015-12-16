Configuration:

    seed_provider:
        - class_name: lt.nkts.cassandra.ConsulSeedProvider
          parameters:
              - consul_url: http://localhost:8500/v1/kv/service/cassandra/sean compile assembly:singleeds/

Build

    mvn clean compile assembly:single
