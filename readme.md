![Build](https://github.com/karnauskas/cassandra-consul-seed-provider/workflows/Build/badge.svg)

### Build

    gradle clean jar shadowjar --warning-mode all

### Configuration
#### Generic

Specify seed provider class in cassandra.yaml config file:

    seed_provider:
      - class_name: lt.nkts.cassandra.ConsulSeedProvider

Specify rest of parameters on command line as standart java properties.

If KV is enabled, provider will look for entries with kv_prefix prefix/namespace otherwise it will try to locate seed
nodes from Consul service catalog.

#### KV

    -Dconsul.kv.enabled=true -Dconsul.kv.prefix=cassandra/seeds

#### ACL

If an acl token is required e.g. via EnvVar:

    -Dconsul.acl.token=$CONSUL_ACL_TOKEN


#### Service


    -Dconsul.url=http://localhost:8500/ -Dconsul.service.tags=tag1 -Dconsul.service.name=cassandra ..

#### Seeding KV storage

    curl -XPUT localhost:8500/v1/kv/cassandra/seeds/192.168.15.15
