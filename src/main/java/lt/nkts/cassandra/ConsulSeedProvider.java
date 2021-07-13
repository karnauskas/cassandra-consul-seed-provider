package lt.nkts.cassandra;

import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.ecwid.consul.v1.OperationException;
import com.ecwid.consul.v1.Response;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.cassandra.locator.SeedProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsulSeedProvider implements SeedProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulSeedProvider.class);
    private ConsulClient client;
    private URL consulURL;
    private Boolean consulUseKV;
    private String consulKVPrefix;
    private String consulServiceName;
    private String consulACLToken;
    private Collection<String> consulServiceTags;
    private List<InetAddress> defaultSeeds;

    ConsulSeedProvider(final Map<String, String> args) {
        defaultSeeds = new ArrayList<InetAddress>();

        if (args != null) {
            final String seeds = args.get("seeds");
            if (seeds != null) {
                for (String host : Splitter.on(",").trimResults().omitEmptyStrings().split(seeds)) {
                    try {
                        defaultSeeds.add(InetAddress.getByName(host));
                    } catch (UnknownHostException ex) {
                        LOGGER.warn("Seed provider couldn't lookup host " + host);
                    }
                }
            }
        }

        try {
            consulURL = new URL(System.getProperty("consul.url", "http://localhost:8500/"));
            consulUseKV = Boolean.valueOf(System.getProperty("consul.kv.enabled", "false"));
            consulKVPrefix = System.getProperty("consul.kv.prefix", "cassandra/seeds");
            consulServiceName = System.getProperty("consul.service.name", "cassandra");
            consulServiceTags = Splitter.on(",").trimResults().omitEmptyStrings()
                    .splitToList(System.getProperty("consul.service.tags", ""));
            consulACLToken = System.getProperty("consul.acl.token", "anonymous");
            if (client == null) {
                client = new ConsulClient(String.format("%s:%s", consulURL.getHost(), consulURL.getPort()));
            }
        } catch (Exception e) {
            LOGGER.error(e.toString());
        }

        LOGGER.debug("consulURL {}", consulURL);
        LOGGER.debug("consulServiceName {}", consulServiceName);
        LOGGER.debug("consulServiceTags {}", consulServiceTags.toString());
        LOGGER.debug("consulServiceTags size [{}]", consulServiceTags.size());
        LOGGER.debug("consulUseKV {}", consulUseKV);
        LOGGER.debug("consulKVPrefix {}", consulKVPrefix);
        LOGGER.debug("consulACLToken {}", consulACLToken);
        LOGGER.debug("default_seeds {}", defaultSeeds);
    }

    public List<InetAddress> getSeeds() {
        try {
            return getSeedsFromConsul();
        } catch (OperationException oe) {
            LOGGER.error("Problem connecting to consul. will attempt to use defaults: " + defaultSeeds.toString(), oe);
            return Collections.unmodifiableList(defaultSeeds);
        }
    }

    private List<InetAddress> getSeedsFromConsul() {
        List<InetAddress> seeds = new ArrayList<InetAddress>();

        if (consulUseKV) {
            final Response<List<GetValue>> response = client.getKVValues(consulKVPrefix, consulACLToken);
            final List<GetValue> all = response.getValue();
            if (all == null) {
                return Collections.unmodifiableList(defaultSeeds);
            }

            for (GetValue gv : all) {
                LOGGER.info("kv: {}", gv);

                String[] parts = gv.getKey().split("/");
                String host = parts[parts.length - 1];

                try {
                    seeds.add(InetAddress.getByName(host));
                } catch (UnknownHostException ex) {
                    LOGGER.warn("Seed provider couldn't lookup host {}", host);
                }
            }

            // TODO change to additional if, Consul Catalog and Consul KV could work together at same time
        } else {
            final Response<List<CatalogService>> response = client.getCatalogService(consulServiceName, null);

            for (CatalogService svc : response.getValue()) {
                try {
                    LOGGER.debug("Service [{}]", svc.toString());
                    String svcAddress = svc.getServiceAddress();
                    final String address = svcAddress == null || svcAddress.length() == 0 ? svc.getAddress()
                            : svcAddress;

                    if (!consulServiceTags.isEmpty()) {
                        Set<String> stags = ImmutableSet.copyOf(svc.getServiceTags());

                        LOGGER.debug("Service tagged with {}", stags.toString());
                        LOGGER.debug("I'm looking for {}", consulServiceTags.toString());

                        if (consulServiceTags.containsAll(stags) && stags.containsAll(consulServiceTags)) {
                            seeds.add(InetAddress.getByName(address));
                        }
                    } else {
                        seeds.add(InetAddress.getByName(address));
                    }

                } catch (Exception e) {
                    LOGGER.warn("Adding seed {}", e.getMessage());
                }
            }
        }

        if (seeds.isEmpty()) {
            LOGGER.info("No seeds found, using defaults");
            seeds.addAll(defaultSeeds);
        }

        LOGGER.info("Seeds {}", seeds.toString());
        return Collections.unmodifiableList(seeds);
    }
}
