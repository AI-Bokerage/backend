package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class PageLayoutTest {

  @Test
  void buildsWithOnlyRequiredFieldsAndDefaultsTheRest() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();

    assertThat(layout.paperSize()).isEqualTo(StandardPaperSize.A4);
    assertThat(layout.orientation()).isEqualTo(StandardOrientation.PORTRAIT);
    assertThat(layout.margins().topPoints()).isZero();
    assertThat(layout.header().text()).isEmpty();
    assertThat(layout.footer().text()).isEmpty();
    assertThat(layout.pageNumbers().enabled()).isFalse();
  }

  @Test
  void buildsWithAllFieldsSet() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.LETTER, StandardOrientation.LANDSCAPE)
            .margins(Margins.uniform(20))
            .header(LayoutHeader.of("Header text"))
            .footer(LayoutFooter.of("Footer text"))
            .pageNumbers(PageNumberConfiguration.enabled("Page {n}"))
            .build();

    assertThat(layout.margins().topPoints()).isEqualTo(20);
    assertThat(layout.header().text()).isEqualTo("Header text");
    assertThat(layout.footer().text()).isEqualTo("Footer text");
    assertThat(layout.pageNumbers().format()).isEqualTo("Page {n}");
  }

  @Test
  void explicitNullOptionalSettersCoalesceToDefaultsRatherThanNull() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT)
            .margins(null)
            .header(null)
            .footer(null)
            .pageNumbers(null)
            .build();

    assertThat(layout.margins()).isNotNull();
    assertThat(layout.margins().topPoints()).isZero();
    assertThat(layout.header()).isNotNull();
    assertThat(layout.footer()).isNotNull();
    assertThat(layout.pageNumbers()).isNotNull();
    assertThat(layout.pageNumbers().enabled()).isFalse();
  }

  @Test
  void layoutHeaderOfNullCoalescesToEmptyText() {
    assertThat(LayoutHeader.of(null).text()).isEmpty();
  }

  @Test
  void layoutFooterOfNullCoalescesToEmptyText() {
    assertThat(LayoutFooter.of(null).text()).isEmpty();
  }

  @Test
  void nullPaperSizeThrowsValidationException() {
    assertThatThrownBy(() -> PageLayout.builder(null, StandardOrientation.PORTRAIT).build())
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullOrientationThrowsValidationException() {
    assertThatThrownBy(() -> PageLayout.builder(StandardPaperSize.A4, null).build())
        .isInstanceOf(ValidationException.class);
  }
}
