package com.company.bsmsvc.storage;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.company.bsmsvc.config.AwsS3Properties;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class InvoiceDocumentAccessService {

    private final AwsS3Properties awsS3Properties;
    private final AmazonS3 amazonS3;
    private final InvoiceStorageKeyGenerator invoiceStorageKeyGenerator;

    public InvoicePdfAccess getPreSignedUrl(PlatformInvoice invoice) {
        String key = invoiceStorageKeyGenerator.generateKey(invoice);
        String bucket = awsS3Properties.getBucketName();
        Duration expiry = Duration.ofMinutes(awsS3Properties.getPresignedUrlExpiryMinutes());
        Instant expiresAtInstant = Instant.now().plus(expiry);

        GeneratePresignedUrlRequest presignedUrlRequest = new GeneratePresignedUrlRequest(bucket, key)
            .withMethod(HttpMethod.GET)
            .withExpiration(Date.from(expiresAtInstant));

        String preSignedUrl = amazonS3.generatePresignedUrl(presignedUrlRequest).toString();
        String expiresAt = DateTimeFormatter.ISO_INSTANT.format(expiresAtInstant);

        return new InvoicePdfAccess(preSignedUrl, expiresAt);
    }
}
