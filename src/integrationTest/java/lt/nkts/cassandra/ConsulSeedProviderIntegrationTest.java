package lt.nkts.cassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers(disabledWithoutDocker = true)
class ConsulSeedProviderIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    private static final GenericContainer<?> CONSUL = new GenericContainer<>(DockerImageName.parse("hashicorp/consul:1.17.0"))
            .withExposedPorts(8500)
            .withCommand("agent", "-dev", "-client=0.0.0.0")
            .withNetwork(NETWORK)
            .waitingFor(Wait.forHttp("/v1/status/leader").forPort(8500).forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    private static final CassandraContainer<?> CASSANDRA_SEED = new CassandraContainer<>(
            DockerImageName.parse("cassandra:4.1.6"))
            .withNetwork(NETWORK)
            .withNetworkAliases("cassandra-seed")
            .withEnv("CASSANDRA_CLUSTER_NAME", "integration-cluster")
            .withEnv("CASSANDRA_ENDPOINT_SNITCH", "GossipingPropertyFileSnitch")
            .withStartupTimeout(Duration.ofMinutes(8));

    @Container
    private static final CassandraContainer<?> CASSANDRA_NODE_2 = new CassandraContainer<>(
            DockerImageName.parse("cassandra:4.1.6"))
            .withNetwork(NETWORK)
            .withNetworkAliases("cassandra-node-2")
            .withEnv("CASSANDRA_CLUSTER_NAME", "integration-cluster")
            .withEnv("CASSANDRA_SEEDS", "cassandra-seed")
            .withEnv("CASSANDRA_ENDPOINT_SNITCH", "GossipingPropertyFileSnitch")
            .withStartupTimeout(Duration.ofMinutes(8));

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("consul.url");
        System.clearProperty("consul.kv.enabled");
        System.clearProperty("consul.kv.prefix");
        System.clearProperty("consul.service.name");
        System.clearProperty("consul.service.tags");
        System.clearProperty("consul.acl.token");
    }

    @Test
    void discoversCassandraNodesFromConsulCatalog() throws Exception {
        String seedIp = containerIp(CASSANDRA_SEED);
        String node2Ip = containerIp(CASSANDRA_NODE_2);

        registerService("cassandra-seed", "cassandra", seedIp, List.of("seed", "integration"));
        registerService("cassandra-node-2", "cassandra", node2Ip, List.of("seed", "integration"));

        System.setProperty("consul.url", consulUrl());
        System.setProperty("consul.service.name", "cassandra");
        System.setProperty("consul.service.tags", "seed,integration");

        ConsulSeedProvider provider = new ConsulSeedProvider(defaultSeeds("127.0.0.1"));
        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertEquals(2, seeds.size());
        assertTrue(seeds.contains(host(seedIp)));
        assertTrue(seeds.contains(host(node2Ip)));
    }

    @Test
    void discoversCassandraNodesFromConsulKv() throws Exception {
        String seedIp = containerIp(CASSANDRA_SEED);

        putKv("cassandra/seeds/" + seedIp, "1");

        System.setProperty("consul.url", consulUrl());
        System.setProperty("consul.kv.enabled", "true");
        System.setProperty("consul.kv.prefix", "cassandra/seeds");

        ConsulSeedProvider provider = new ConsulSeedProvider(defaultSeeds("127.0.0.1"));
        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertEquals(1, seeds.size());
        assertTrue(seeds.contains(host(seedIp)));
    }

    private static Map<String, String> defaultSeeds(String values) {
        Map<String, String> args = new HashMap<>();
        args.put("seeds", values);
        return args;
    }

    private static String containerIp(GenericContainer<?> container) {
        return container.getCurrentContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .values()
                .iterator()
                .next()
                .getIpAddress();
    }

    private static void registerService(String id, String name, String address, List<String> tags)
            throws IOException, InterruptedException {
        if (tags.isEmpty()) {
            throw new IllegalArgumentException("tags must not be empty");
        }
        String tagsJson = tags.stream().map(tag -> "\"" + tag + "\"").collect(Collectors.joining(","));
        String payload = String.format(
                "{\"ID\":\"%s\",\"Name\":\"%s\",\"Address\":\"%s\",\"Port\":9042,\"Tags\":[%s]}",
                id, name, address, tagsJson);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(consulApiUri("v1/agent/service/register"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Consul service registration failed");
    }

    private static void putKv(String key, String value) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(consulApiUri("v1/kv/" + key))
                .timeout(Duration.ofSeconds(20))
                .PUT(HttpRequest.BodyPublishers.ofString(value))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Consul KV write failed");
    }

    private static String consulUrl() {
        return "http://" + CONSUL.getHost() + ":" + CONSUL.getMappedPort(8500) + "/";
    }

    private static URI consulApiUri(String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return URI.create(consulUrl() + normalizedPath);
    }

    private static InetAddressAndPort host(String value) throws UnknownHostException {
        return InetAddressAndPort.getByName(value);
    }
}
