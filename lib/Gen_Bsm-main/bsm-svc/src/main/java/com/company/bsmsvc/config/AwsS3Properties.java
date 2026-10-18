package com.company.bsmsvc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aws.s3")
@Getter
@Setter
public class AwsS3Properties {
    private String bucketName;
    private String region;
    private String accessKey;
    private String secretKey;
    private int presignedUrlExpiryMinutes = 15;
}
