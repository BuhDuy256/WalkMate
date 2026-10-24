package com.walkmate.matching.support;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.application.walkintent.WalkIntentCommandService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.Map;

/**
 * One self-contained Spring application context representing a single deployed instance:
 * its own Hikari pool, transaction manager and service graph. Multiple instances can share
 * the same PostgreSQL database to exercise cross-instance races.
 */
public class MatchingTestInstance implements AutoCloseable {

    private final AnnotationConfigApplicationContext context;

    private MatchingTestInstance(AnnotationConfigApplicationContext context) {
        this.context = context;
    }

    public static MatchingTestInstance start(String jdbcUrl, String poolName) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        Map<String, Object> props = new HashMap<>();
        props.put("test.datasource.url", jdbcUrl);
        props.put("test.datasource.username", "test");
        props.put("test.datasource.password", "test");
        props.put("test.datasource.pool-name", poolName);
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("matching-test", props));
        ctx.register(MatchingTestConfiguration.class);
        ctx.refresh();
        return new MatchingTestInstance(ctx);
    }

    public WalkIntentCommandService walkIntentCommandService() {
        return context.getBean(WalkIntentCommandService.class);
    }

    public MatchingCommandService matchingCommandService() {
        return context.getBean(MatchingCommandService.class);
    }

    public JdbcClient jdbcClient() {
        return context.getBean(JdbcClient.class);
    }

    @Override
    public void close() {
        context.close();
    }
}
