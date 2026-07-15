package com.overzealouspelican.awsui.panel;

import java.time.Duration;

record LogsTimeframeOption(String label, Duration duration) {
    @Override
    public String toString() {
        return label;
    }
}