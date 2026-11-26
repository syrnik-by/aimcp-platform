package by.syrnik.aimcp.runner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties("aimcp.runner")
@ConstructorBinding
public class RunnerSettings {

    private final String token;
    private final String profile;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final Duration maskingBudget;

    public RunnerSettings(String token, String profile, int maxRequestBytes, int maxResponseBytes, Duration maskingBudget) {

        if (token != null && token.length() >= 32) {
            this.token = token;
        } else {
            throw new IllegalArgumentException("AIMCP_RUNNER_TOKEN не задан или короче 32 символов");
        }

        this.profile = profile;
        if (maxRequestBytes >= 1 && maxResponseBytes >= 1) {
            this.maxRequestBytes = maxRequestBytes;
            this.maxResponseBytes = maxResponseBytes;
        } else {
            throw new IllegalArgumentException("проверьте значения лимитов запросов");
        }

        if (!maskingBudget.isNegative() && !maskingBudget.isZero()) {
            this.maskingBudget = maskingBudget;
        } else {
            throw new IllegalArgumentException("бюджет маскирования должен быть больше 0");
        }
    }

    public String getToken() {
        return token;
    }

    public String getProfile() {
        return profile;
    }

    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public Duration getMaskingBudget() {
        return maskingBudget;
    }
}
