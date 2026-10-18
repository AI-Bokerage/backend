package com.example.authsvc.infrastructure.email.config;

import com.example.authsvc.config.properties.AwsProperties;
import com.example.authsvc.config.properties.MailProperties;
import com.example.authsvc.infrastructure.email.provider.EmailProvider;
import com.example.authsvc.infrastructure.email.provider.SesEmailProvider;
import com.example.authsvc.infrastructure.email.provider.SmtpEmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

/**
 * Creates the active {@link EmailProvider} bean based on {@code mail.provider}.
 *
 * <p>This entire configuration class is gated by {@code app.email.enabled=true} —
 * when absent or false, no email beans are registered at all, no SMTP/SES
 * connection is ever attempted, and no email credentials are demanded.
 *
 * <table>
 *   <tr><th>mail.provider</th><th>Bean created</th></tr>
 *   <tr><td>smtp (default)</td><td>{@link SmtpEmailProvider}</td></tr>
 *   <tr><td>ses</td><td>{@link SesEmailProvider} + {@link SesClient}</td></tr>
 * </table>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EmailProviderConfig {

    private final MailProperties  mailProperties;
    private final AwsProperties   awsProperties;

    @Bean
    @ConditionalOnProperty(name = "mail.provider", havingValue = "ses")
    public SesClient sesClient() {
        String region = mailProperties.getSes().getRegion();
        SesClientBuilder builder = SesClient.builder().region(Region.of(region));

        String accessKey = awsProperties.getAccessKeyId();
        String secretKey = awsProperties.getSecretAccessKey();
        if (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
            log.info("email.provider.ses ses.client.initialized region={} credentials=static", region);
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.info("email.provider.ses ses.client.initialized region={} credentials=default-chain", region);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "mail.provider", havingValue = "ses")
    public EmailProvider sesEmailProvider(SesClient sesClient) {
        String from = mailProperties.getSes().getFromAddress();
        log.info("email.provider.ses initialized fromAddress={}", from);
        return new SesEmailProvider(sesClient, from);
    }

    @Bean
    @ConditionalOnProperty(name = "mail.provider", havingValue = "smtp", matchIfMissing = true)
    public EmailProvider smtpEmailProvider(JavaMailSender javaMailSender) {
        log.info("email.provider.smtp initialized");
        return new SmtpEmailProvider(javaMailSender);
    }
}
