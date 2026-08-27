package com.antshorttv.commercial;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialFulfillmentRunner {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(Runnable fulfillment) {
        fulfillment.run();
    }
}
