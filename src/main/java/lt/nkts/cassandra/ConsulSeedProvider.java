package lt.nkts.cassandra;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.OperationException;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.apache.cassandra.locator.SeedProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;

public class ConsulSeedProvider implements SeedProvider {

    private static final Logger logger = LoggerFactory.getLogger(ConsulSeedProvider.class);

    private ConsulClient client;

    private URL consul_url;
    private Boolean consul_use_kv;
    private String consul_kv_prefix;
    private String consul_service_name;
    private String consul_acl_token;
    private Collection<String> consul_service_tags;
    private List<InetAddress> default_seeds;

    public ConsulSeedProvider(Map<String, String> args) {
        // These are used as a fallback if we get nothing from Consul
        default_seeds = new ArrayList<InetAddress>();
        String seeds = args.get("seeds");
        if (seeds != null) {
            for (String host : Splitter.on(",").trimResults().omitEmptyStrings().split(seeds)) {
                try {
                    default_seeds.add(InetAddress.getByName(host));
                } catch (UnknownHostException ex) {
                    logger.warn("Seed provider couldn't lookup host " + host);
                }
            }
        }

        try {
            consul_url = new URL(System.getProperty("consul.url", "http://localhost:8500/"));
            consul_use_kv = Boolean.valueOf(System.getProperty("consul.kv.enabled", "false"));
            consul_kv_prefix = System.getProperty("consul.kv.prefix", "cassandra/seeds");
            consul_service_name = System.getProperty("consul.service.name", "cassandra");
            consul_service_tags = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(System.getProperty("consul.service.tags", ""));
            consul_acl_token = System.getProperty("consul.acl.token", "anonymous");
        } catch (Exception e) {
            logger.error(e.toString());
        }

        logger.debug("consul_url {}", consul_url);
        logger.debug("consul_service_name {}", consul_service_name);
        logger.debug("consul_service_tags {}", consul_service_tags.toString());
        logger.debug("consul_service_tags size [{}]", consul_service_tags.size());
        logger.debug("consul_use_kv {}", consul_use_kv);
        logger.debug("consul_kv_prefix {}", consul_kv_prefix);
        logger.debug("consul_acl_token {}", consul_acl_token);
        logger.debug("default_seeds {}", default_seeds);
    }

    public List<InetAddress> getSeeds() {
        try {
            return getSeedsFromConsul();
        } catch (OperationException oe) {
            logger.error("Problem connecting to consul. will attempt to use defaults: " + default_seeds.toString(), oe);
            return Collections.unmodifiableList(default_seeds);
        }
    }

    private  List<InetAddress> getSeedsFromConsul(){
        if (client == null) {
            client = new ConsulClient(String.format("%s:%s", consul_url.getHost(), consul_url.getPort()));
        }

        List<InetAddress> seeds = new ArrayList<InetAddress>();

        if (consul_use_kv) {
            final Response<List<GetValue>> response = client.getKVValues(consul_kv_prefix, consul_acl_token);
            List<GetValue> all = response.getValue();
            if (all == null) {
                return Collections.unmodifiableList(default_seeds);
            }

            for (GetValue gv : all) {
                logger.info("kv: {}", gv);

                String[] parts = gv.getKey().split("/");
                String host = parts[parts.length - 1];

                try {
                    seeds.add(InetAddress.getByName(host));
                } catch (UnknownHostException ex) {
                    logger.warn("Seed provider couldn't lookup host {}", host);
                }
            }

        } else {
            final Response<List<CatalogService>> response = client.getCatalogService(consul_service_name, null);

            for (CatalogService svc : response.getValue()) {
                try {
                    logger.debug("Service [{}]", svc.toString());
                    String svcAddress =  svc.getServiceAddress();
                    final String address = (svcAddress == null || svcAddress.length() == 0) ? svc.getAddress() : svcAddress;

                    if (!consul_service_tags.isEmpty()) {
                        Set<String> stags = ImmutableSet.copyOf(svc.getServiceTags());

                        logger.debug("Service tagged with {}", stags.toString());
                        logger.debug("I'm looking for {}", consul_service_tags.toString());

                        if (consul_service_tags.containsAll(stags) && stags.containsAll(consul_service_tags)) {
                            seeds.add(InetAddress.getByName(address));
                        }
                    } else {
                        seeds.add(InetAddress.getByName(address));
                    }

                } catch (Exception e) {
                    logger.warn("Adding seed {}", e.getMessage());
                }
            }
        }
        if (seeds.isEmpty()) {
            // We got nothing from Consul so add default seeds
            seeds.addAll(default_seeds);
            logger.info("No seeds found, using defaults");
        }
        logger.info("Seeds {}", seeds.toString());
        return Collections.unmodifiableList(seeds);
    }
}
