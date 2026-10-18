package io.cpms.common.errors;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class NotFoundException extends PlatformException {

    public NotFoundException(String resource) {
        super(resource.toUpperCase() + "_NOT_FOUND",
              resource + " not found",
              HttpStatus.NOT_FOUND,
              Map.of("resource", resource));
    }

    public NotFoundException(String resource, String id) {
        super(resource.toUpperCase() + "_NOT_FOUND",
              resource + " with id " + id + " not found",
              HttpStatus.NOT_FOUND,
              Map.of("resource", resource, "id", id));
    }
}
