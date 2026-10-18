package com.company.ppmsvc.addonprice.usecase;

import com.company.ppmsvc.addonprice.model.AddOnPrice;
import com.company.ppmsvc.addonprice.port.AddOnPriceRepositoryPort;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddOnPriceApplicationServiceImpl implements AddOnPriceApplicationService {

    private final AddOnPriceRepositoryPort addOnPriceRepositoryPort;

    @Override
    public AddOnPrice resolveActivePrice(UUID addOnId, String region, String currency, BillingCycle cycle) {
        log.debug("[AddOnPriceService] resolveActivePrice addOnId={} region={} currency={} cycle={}",
            addOnId, region, currency, cycle);

        AddOnPrice price = addOnPriceRepositoryPort
            .findActivePrice(addOnId, region, currency, cycle)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ADD_ON_PRICE_NOT_FOUND,
                "No active add-on price found for addOnId=" + addOnId
                    + " region=" + region + " currency=" + currency + " cycle=" + cycle));

        log.debug("[AddOnPriceService] resolved addOnId={} priceId={} amount={}",
            addOnId, price.getId(), price.getAmount());

        return price;
    }
}
