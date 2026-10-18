// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/StepCallResult.java
package com.example.tnt_svc.saga;

import java.util.Map;

public record StepCallResult(boolean success, Map<String, Object> context, String error) {

    public static StepCallResult success(Map<String, Object> context) {
        return new StepCallResult(true, context, null);
    }

    public static StepCallResult failure(String error) {
        return new StepCallResult(false, Map.of(), error);
    }
}
