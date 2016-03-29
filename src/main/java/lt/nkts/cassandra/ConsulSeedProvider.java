package lt.nkts.cassandra;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.kv.model.GetValue;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.locator.SeedProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class ConsulSeedProvider implements SeedProvider {

    private static final Logger logger = LoggerFactory.getLogger(ConsulSeedProvider.class);
    private ConsulClient client;

    public ConsulSeedProvider(final Map<String, String> args) {
    }

    public List<InetAddress> getSeeds() {
        Config config = null;
        URL consul_url = null;
        String kv_prefix = "";
        String service_name = "";
        Boolean use_kv = false;

        try {
            config = DatabaseDescriptor.loadConfig();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        try {
            consul_url = new URL(config.seed_provider.parameters.getOrDefault("consul_url", "http://localhost:8500/"));
            logger.debug("consul_url: {}", consul_url);

            service_name = config.seed_provider.parameters.getOrDefault("consul_service", "cassandra");
            logger.debug("consul_service: {}", service_name);

            use_kv = Boolean.valueOf(config.seed_provider.parameters.get("use_kv"));
            logger.debug("use_kv: {}", use_kv.toString());

            kv_prefix = config.seed_provider.parameters.getOrDefault("kv_prefix", "cassandra/seeds");
            logger.debug("kv_prefix: {}", kv_prefix);
        } catch (Exception e) {
            logger.error("Error parsing params: {}", e.getMessage());
        }

        client = new ConsulClient(String.format("%s:%s", consul_url.getHost(), consul_url.getPort()));


        List<InetAddress> seeds = new ArrayList<InetAddress>();

        if (use_kv) {
            Response response = client.getKVValues(kv_prefix);
            List all = (ArrayList<GetValue>) response.getValue();
            if (all == null) {
                return Collections.EMPTY_LIST;
            }

            for (Object gv : all) {
                logger.info("kv: {}", gv);

                GetValue record = (GetValue) gv;
                String[] parts = record.getKey().split("/");
                String host = parts[parts.length - 1];

                try {
                    seeds.add(InetAddress.getByName(host));
                } catch (UnknownHostException ex) {
                    logger.warn("Seed provider couldn't lookup host {}", host);
                }
            }

        } else {
            Response<List<CatalogService>> response = client.getCatalogService(service_name, null);

            for(CatalogService svc: response.getValue()) {
                if (svc.getServiceId().equals(service_name)) {
                    try {
                        seeds.add(InetAddress.getByName(svc.getServiceAddress()));
                    } catch (Exception e) {
                        logger.warn("Adding seed: {}", e.getMessage());
                    }
                }
            }
        }

        if (seeds.size() > 0) {
            logger.info("Seeds: {}", seeds.toString());
        }

        return Collections.unmodifiableList(seeds);
    }

}
