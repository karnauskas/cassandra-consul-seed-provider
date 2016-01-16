Configuration:

    seed_provider:
        - class_name: lt.nkts.cassandra.ConsulSeedProvider
          parameters:
              - consul_url: http://localhost:8500
              - consul_prefix: cassandra/seeds

Build:

    mvn clean compile assembly:single
