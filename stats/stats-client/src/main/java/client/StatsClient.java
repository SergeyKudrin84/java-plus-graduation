package client;

import ewm.dto.EndpointHitDto;
import ewm.dto.ViewStatsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class StatsClient {

    private final RestClient restClient;
    private static final String SERVICE_ID = "stats-server";
    //private final String serverUrl;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DiscoveryClient discoveryClient;

//    public StatsClient(@Value("${stats-server.url}") String serverUrl) {
//        this.serverUrl = serverUrl;
//        this.restClient = RestClient.builder()
//                .baseUrl(serverUrl)
//                .build();
//    }

    public StatsClient(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.restClient = RestClient.builder().build();
    }

    public ResponseEntity<Object> saveHit(EndpointHitDto hitDto) {
        URI uri = getStatsServerUri("/hit");
        return restClient.post()
                .uri(uri)
                .body(hitDto)
                .retrieve()
                .toEntity(Object.class);
    }

    public ResponseEntity<Object> getStats(LocalDateTime start,
                                           LocalDateTime end,
                                           List<String> uris,
                                           Boolean unique) {
        URI baseUri = getStatsServerUri("/stats");

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUri(baseUri)
                .queryParam("start", start.format(FORMATTER))
                .queryParam("end", end.format(FORMATTER))
                .queryParam("unique", unique);

        if (uris != null && !uris.isEmpty()) {
            uris.forEach(uri -> builder.queryParam("uris", uri));
        }

        URI uri = builder.build().encode().toUri();

        return restClient.get()
                .uri(uri)
                .retrieve()
                .toEntity(Object.class);
    }

    private URI getStatsServerUri(String path) {

        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_ID);

        if (instances.isEmpty()) {
            throw new IllegalStateException("Сервис stats-server не найден в Eureka");
        }

        ServiceInstance instance = instances.get(0);

        return UriComponentsBuilder
                .fromUri(instance.getUri())
                .path(path)
                .build()
                .toUri();
    }
}