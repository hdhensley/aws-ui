package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.EcsService;

final class EcsServiceDetailsFormatter {
    private EcsServiceDetailsFormatter() {
    }

    static String format(EcsService.ServiceRow row) {
        return "Service: " + row.name() + System.lineSeparator()
            + "ARN: " + row.arn() + System.lineSeparator()
            + "Status: " + row.status() + System.lineSeparator()
            + "Launch Type: " + row.launchType() + System.lineSeparator()
            + "Scheduling: " + row.schedulingStrategy() + System.lineSeparator()
            + "Task Definition: " + row.taskDefinition() + System.lineSeparator()
            + "Deployments: " + row.deploymentStatus() + System.lineSeparator()
            + "Last Deployment: " + row.lastDeploymentAt() + System.lineSeparator()
            + "Tasks: desired=" + row.desiredCount()
            + ", running=" + row.runningCount()
            + ", pending=" + row.pendingCount() + System.lineSeparator()
            + "Created At: " + row.createdAt();
    }
}