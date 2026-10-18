package com.company.bsmsvc.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.company.bsmsvc.config.AwsS3Properties;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.storage.InvoiceStorageKeyGenerator;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3InvoiceDocumentStorageAdapter implements InvoiceDocumentStoragePort {

    private final AwsS3Properties awsS3Properties;
    private final S3Client s3Client;
    private final InvoiceStorageKeyGenerator invoiceStorageKeyGenerator;

    @Override
    public String uploadInvoicePdf(PlatformInvoice invoice, byte[] pdfContent, String contentType) {
        String key = invoiceStorageKeyGenerator.generateKey(invoice);
        PutObjectRequest req = PutObjectRequest.builder()
            .bucket(awsS3Properties.getBucketName())
            .key(key)
            .contentType(contentType)
            .build();
        s3Client.putObject(req, RequestBody.fromBytes(pdfContent));
        String url = String.format("https://%s.s3.%s.amazonaws.com/%s", awsS3Properties.getBucketName(), awsS3Properties.getRegion(), key);
        log.info("[uploadInvoicePdf] uploaded key={} url={}", key, url);
        return url;
    }
}
