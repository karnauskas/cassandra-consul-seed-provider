package lt.nkts.cassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.OperationException;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.CatalogServiceRequest;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.kv.model.GetValue;
import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConsulSeedProviderTest {

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
    void returnsDefaultSeedsWhenConsulRequestFails() throws Exception {
        ConsulClient client = mock(ConsulClient.class);
        when(client.getCatalogService(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(CatalogServiceRequest.class)))
                .thenThrow(new OperationException(500, "failure", "boom"));

        ConsulSeedProvider provider = createProvider("127.0.0.1,127.0.0.2", client);

        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertIterableEquals(List.of(host("127.0.0.1"), host("127.0.0.2")), seeds);
    }

    @Test
    void returnsDefaultSeedsWhenKvLookupReturnsNoValues() throws Exception {
        System.setProperty("consul.kv.enabled", "true");
        ConsulClient client = mock(ConsulClient.class);
        when(client.getKVValues(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new Response<>(null, 1L, true, 0L));

        ConsulSeedProvider provider = createProvider("127.0.0.1", client);

        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertIterableEquals(List.of(host("127.0.0.1")), seeds);
    }

    @Test
    void returnsKvSeedsAndSkipsInvalidHosts() throws Exception {
        System.setProperty("consul.kv.enabled", "true");
        System.setProperty("consul.kv.prefix", "cassandra/seeds");
        ConsulClient client = mock(ConsulClient.class);

        GetValue valid = new GetValue();
        valid.setKey("cassandra/seeds/127.0.0.1");
        GetValue invalid = new GetValue();
        invalid.setKey("cassandra/seeds/not-a-host");

        when(client.getKVValues(org.mockito.ArgumentMatchers.eq("cassandra/seeds"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new Response<>(List.of(valid, invalid), 1L, true, 0L));

        ConsulSeedProvider provider = createProvider("127.0.0.9", client);

        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertIterableEquals(List.of(host("127.0.0.1")), seeds);
    }

    @Test
    void returnsCatalogSeedsUsingServiceAddressAndNodeAddressFallback() throws Exception {
        ConsulClient client = mock(ConsulClient.class);

        CatalogService withServiceAddress = new CatalogService();
        withServiceAddress.setAddress("127.0.0.20");
        withServiceAddress.setServiceAddress("127.0.0.21");
        withServiceAddress.setServiceTags(List.of("seed"));

        CatalogService withNodeAddress = new CatalogService();
        withNodeAddress.setAddress("127.0.0.22");
        withNodeAddress.setServiceAddress("");
        withNodeAddress.setServiceTags(List.of("seed"));

        when(client.getCatalogService(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(CatalogServiceRequest.class)))
                .thenReturn(new Response<>(List.of(withServiceAddress, withNodeAddress), 1L, true, 0L));

        ConsulSeedProvider provider = createProvider("127.0.0.1", client);

        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertIterableEquals(List.of(host("127.0.0.21"), host("127.0.0.22")), seeds);
    }

    @Test
    void filtersCatalogSeedsByExactTagSet() throws Exception {
        System.setProperty("consul.service.tags", "seed,prod");
        ConsulClient client = mock(ConsulClient.class);

        CatalogService exactMatch = new CatalogService();
        exactMatch.setAddress("127.0.0.30");
        exactMatch.setServiceAddress("127.0.0.30");
        exactMatch.setServiceTags(List.of("seed", "prod"));

        CatalogService partialMatch = new CatalogService();
        partialMatch.setAddress("127.0.0.31");
        partialMatch.setServiceAddress("127.0.0.31");
        partialMatch.setServiceTags(List.of("seed"));

        CatalogService extraTag = new CatalogService();
        extraTag.setAddress("127.0.0.32");
        extraTag.setServiceAddress("127.0.0.32");
        extraTag.setServiceTags(List.of("seed", "prod", "extra"));

        when(client.getCatalogService(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(CatalogServiceRequest.class)))
                .thenReturn(new Response<>(List.of(exactMatch, partialMatch, extraTag), 1L, true, 0L));

        ConsulSeedProvider provider = createProvider("127.0.0.99", client);

        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertIterableEquals(List.of(host("127.0.0.30")), seeds);
    }

    @Test
    void fallsBackToDefaultSeedsWhenCatalogProducesNoValidHosts() throws Exception {
        ConsulClient client = mock(ConsulClient.class);

        CatalogService invalid = new CatalogService();
        invalid.setAddress("not-a-host");
        invalid.setServiceAddress("not-a-host");
        invalid.setServiceTags(List.of("seed"));

        when(client.getCatalogService(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(CatalogServiceRequest.class)))
                .thenReturn(new Response<>(List.of(invalid), 1L, true, 0L));

        ConsulSeedProvider provider = createProvider("127.0.0.40", client);

        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertIterableEquals(List.of(host("127.0.0.40")), seeds);
    }

    @Test
    void ignoresInvalidDefaultSeedsFromArguments() throws Exception {
        ConsulClient client = mock(ConsulClient.class);
        when(client.getCatalogService(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(CatalogServiceRequest.class)))
                .thenThrow(new OperationException(500, "failure", "boom"));

        ConsulSeedProvider provider = createProvider("127.0.0.50, bad-host ,127.0.0.51", client);

        List<InetAddressAndPort> seeds = provider.getSeeds();

        assertEquals(List.of(host("127.0.0.50"), host("127.0.0.51")), seeds);
    }

    private static ConsulSeedProvider createProvider(String defaultSeeds, ConsulClient client) throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put("seeds", defaultSeeds);
        ConsulSeedProvider provider = new ConsulSeedProvider(args);
        Field clientField = ConsulSeedProvider.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(provider, client);
        return provider;
    }

    private static InetAddressAndPort host(String value) throws UnknownHostException {
        return InetAddressAndPort.getByName(value);
    }
}
