package lt.nkts.cassandra;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.google.common.base.Splitter;
import org.apache.cassandra.locator.SeedProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
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
    private Collection<String> consul_service_tags;

    public ConsulSeedProvider(Map<String, String> args) {
        try {
            consul_url = new URL(System.getProperty("consul.url", "http://localhost:8500/"));
            consul_use_kv = BooleanUtils.toBoolean(System.getProperty("consul.kv.enabled", "false"), "true", "false");
            consul_kv_prefix = System.getProperty("consul.kv.prefix", "cassandra/seeds");
            consul_service_name = System.getProperty("consul.service.name", "cassandra");
            consul_service_tags = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(System.getProperty("consul.service.tags", ""));
        } catch (Exception e) {
            logger.error(e.toString());
        }

        logger.debug("consul_url {}", consul_url);
        logger.debug("consul_service_name {}", consul_service_name);
        logger.debug("consul_service_tags {}", consul_service_tags.toString());
        logger.debug("consul_service_tags size [{}]", consul_service_tags.size());
        logger.debug("consul_use_kv {}", consul_use_kv);
        logger.debug("consul_kv_prefix {}", consul_kv_prefix);
    }

    public List<InetAddress> getSeeds() {
        client = new ConsulClient(String.format("%s:%s", consul_url.getHost(), consul_url.getPort()));

        List<InetAddress> seeds = new ArrayList<InetAddress>();

        if (consul_use_kv) {
            Response response = client.getKVValues(consul_kv_prefix);
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
            Response<List<CatalogService>> response = client.getCatalogService(consul_service_name, null);

            for (CatalogService svc : response.getValue()) {

                if (svc.getServiceId().equals(consul_service_name)) {
                    try {
                        logger.debug("Service [{}]", svc.toString());

                        if (CollectionUtils.isNotEmpty(consul_service_tags)) {
                            List<String> stags = svc.getServiceTags();

                            logger.debug("Service tagged with {}", stags.toString());
                            logger.debug("I'm looking for {}", consul_service_tags.toString());

                            if (CollectionUtils.containsAll(stags, consul_service_tags)) {
                                seeds.add(InetAddress.getByName(svc.getServiceAddress()));
                            }
                        } else {
                            seeds.add(InetAddress.getByName(svc.getServiceAddress()));
                        }

                    } catch (Exception e) {
                        logger.warn("Adding seed {}", e.getMessage());
                    }
                }

            }
        }

        if (seeds.size() > 0) {
            logger.info("Seeds {}", seeds.toString());
        }

        return Collections.unmodifiableList(seeds);
    }
}
