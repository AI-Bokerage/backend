package io.genfin.invoice.invoice;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ConcurrentBuilderTest {

  @Test
  void concurrentBuildsProduceDistinctIndependentInvoices() throws Exception {
    int count = 100;
    List<Callable<Invoice>> tasks =
        IntStream.range(0, count)
            .<Callable<Invoice>>mapToObj(
                i ->
                    () ->
                        InvoiceBuilder.newInvoice()
                            .currency(USD)
                            .dueDate(Instant.parse("2026-02-01T00:00:00Z"))
                            .clockProvider(ClockProviders.system())
                            .actor("tester-" + i)
                            .build())
            .toList();

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      List<Future<Invoice>> futures = tasks.stream().map(pool::submit).toList();
      List<Invoice> invoices = futures.stream().map(this::get).collect(Collectors.toList());

      assertThat(invoices.stream().map(Invoice::id).distinct()).hasSize(count);
    }
  }

  private Invoice get(Future<Invoice> future) {
    try {
      return future.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
