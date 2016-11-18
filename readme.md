### Status
[![Build Status](https://travis-ci.org/nkts/cassandra-consul-seed-provider.svg?branch=master)](https://travis-ci.org/nkts/cassandra-consul-seed-provider)
[![Code Climate](https://codeclimate.com/github/nkts/cassandra-consul-seed-provider/badges/gpa.svg)](https://codeclimate.com/github/nkts/cassandra-consul-seed-provider)
[![Test Coverage](https://codeclimate.com/github/nkts/cassandra-consul-seed-provider/badges/coverage.svg)](https://codeclimate.com/github/nkts/cassandra-consul-seed-provider/coverage)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/38b51df63b5f4be8ad638e440c5bccb1)](https://www.codacy.com/app/marius-karnauskas/cassandra-consul-seed-provider?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=nkts/cassandra-consul-seed-provider&amp;utm_campaign=Badge_Grade)
[![Coverage Status](https://coveralls.io/repos/github/nkts/cassandra-consul-seed-provider/badge.svg?branch=dynamic_version)](https://coveralls.io/github/nkts/cassandra-consul-seed-provider?branch=dynamic_version)

### Build

Before building, make sure that the Cassandra dependency version matches your version :

	compile 'org.apache.cassandra:cassandra-all:3.7'

Then build:

    gradle shadowJar
    gradle jar

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

