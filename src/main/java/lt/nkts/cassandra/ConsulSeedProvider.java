package lt.nkts.cassandra;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import org.apache.cassandra.locator.SeedProvider;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.json.simple.parser.JSONParser;
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
    private JSONParser jsonParser = new JSONParser();

    public ConsulSeedProvider(final Map<String, String> args) {}

    public List<InetAddress> getSeeds() {
        Config config = null;
        URL consulUrl = null;
        String prefix = "";

        try {
            config = DatabaseDescriptor.loadConfig();
            logger.debug(config.seed_provider.toString());
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        try {
            consulUrl = new URL(config.seed_provider.parameters.getOrDefault("consul_url", "http://localhost:8500/"));
            logger.debug("Consul url: {}", consulUrl);
        } catch (Exception e) {
            logger.error("Error getting consul_url: {}", e.getMessage());
        }

        try {
            prefix = config.seed_provider.parameters.getOrDefault("consul_prefix", "cassandra/seeds");
            logger.info("Consul prefix: {}", prefix);
        } catch (Exception e) {
            logger.warn("Error getting consul_prefix: {}", e.getMessage());
        }

        client = new ConsulClient(String.format("%s:%s", consulUrl.getHost(), consulUrl.getPort()));

        Response response = client.getKVValues(prefix);
        List all = (ArrayList<GetValue>) response.getValue();
        if (all == null) {
            return new ArrayList();
        }
        List<InetAddress> seeds = new ArrayList<InetAddress>(all.size());

        for (Object gv : all) {
            GetValue record = (GetValue) gv;
            String[] parts = record.getKey().split("/");
            String host = parts[parts.length - 1];

            try {
                seeds.add(InetAddress.getByName(host));
            } catch (UnknownHostException ex) {
                logger.warn("Seed provider couldn't lookup host {}", host);
            }
        }

        if (seeds.size() > 0) {
            logger.info("Seeds: {}", seeds.toString());
        }

        return Collections.unmodifiableList(seeds);

    }

}
