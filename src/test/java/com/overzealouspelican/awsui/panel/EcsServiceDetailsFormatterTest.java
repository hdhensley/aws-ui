package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.EcsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EcsServiceDetailsFormatterTest {

    @Test
    void formatsServiceFieldsInReadableOrder() {
        EcsService.ServiceRow row = new EcsService.ServiceRow(
            "orders-api",
            "arn:aws:ecs:us-east-1:123456789012:service/orders-api",
            "ACTIVE",
            "FARGATE",
            "REPLICA",
            3,
            2,
            1,
            "orders-api:42",
            "PRIMARY",
            "2026-07-09 12:00:00",
            "2026-07-01 08:00:00"
        );

        String formatted = EcsServiceDetailsFormatter.format(row);

        assertTrue(formatted.contains("Service: orders-api"));
        assertTrue(formatted.contains("Task Definition: orders-api:42"));
        assertTrue(formatted.contains("Tasks: desired=3, running=2, pending=1"));
        assertTrue(formatted.contains("Created At: 2026-07-01 08:00:00"));
    }
}