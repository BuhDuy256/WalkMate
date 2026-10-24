package com.walkmate.gps.support;

import com.walkmate.application.tracking.TrackingCommandServiceV2;
import com.walkmate.application.tracking.TrackingQueryService;
import com.walkmate.application.tracking.TrackingSealService;
import com.walkmate.domain.tracking.TrackingChunkRepository;
import com.walkmate.domain.tracking.TrackingStreamRepository;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.Map;

/**
 * One self-contained Spring application context representing a single
 * deployed instance of the v2 GPS write path: its own Hikari pool,
 * transaction manager and service graph.
 */
public class GpsTestInstance implements AutoCloseable {

    private final AnnotationConfigApplicationContext context;

    private GpsTestInstance(AnnotationConfigApplicationContext context) {
        this.context = context;
    }

    public static GpsTestInstance start(String jdbcUrl, String poolName) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        Map<String, Object> props = new HashMap<>();
        props.put("test.datasource.url", jdbcUrl);
        props.put("test.datasource.username", "test");
        props.put("test.datasource.password", "test");
        props.put("test.datasource.pool-name", poolName);
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("gps-test", props));
        ctx.register(GpsTestConfiguration.class);
        ctx.refresh();
        return new GpsTestInstance(ctx);
    }

    public TrackingCommandServiceV2 commandService() {
        return context.getBean(TrackingCommandServiceV2.class);
    }

    public TrackingQueryService queryService() {
        return context.getBean(TrackingQueryService.class);
    }

    public TrackingSealService sealService() {
        return context.getBean(TrackingSealService.class);
    }

    public TrackingStreamRepository streamRepository() {
        return context.getBean(TrackingStreamRepository.class);
    }

    public TrackingChunkRepository chunkRepository() {
        return context.getBean(TrackingChunkRepository.class);
    }

    public JdbcClient jdbcClient() {
        return context.getBean(JdbcClient.class);
    }

    @Override
    public void close() {
        context.close();
    }
}
