package io.genfin.document.api.brand;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.BrandId;

public final class BrandProfile {

  private final BrandId id;
  private final BrandIdentity identity;
  private final BrandAssets assets;
  private final BrandContactInformation contact;
  private final BrandRegistrationInformation registration;
  private final BrandHeader header;
  private final BrandFooter footer;

  private BrandProfile(Builder builder) {
    Validate.notNull(builder.id, "id must not be null");
    Validate.notNull(builder.identity, "identity must not be null");
    this.id = builder.id;
    this.identity = builder.identity;
    this.assets = builder.assets == null ? BrandAssets.empty() : builder.assets;
    this.contact =
        builder.contact == null ? BrandContactInformation.of("", "", "") : builder.contact;
    this.registration =
        builder.registration == null
            ? BrandRegistrationInformation.of("", "")
            : builder.registration;
    this.header = builder.header == null ? BrandHeader.of("") : builder.header;
    this.footer = builder.footer == null ? BrandFooter.of("") : builder.footer;
  }

  public static Builder builder(BrandId id, BrandIdentity identity) {
    return new Builder(id, identity);
  }

  public BrandId id() {
    return id;
  }

  public BrandIdentity identity() {
    return identity;
  }

  public BrandAssets assets() {
    return assets;
  }

  public BrandContactInformation contact() {
    return contact;
  }

  public BrandRegistrationInformation registration() {
    return registration;
  }

  public BrandHeader header() {
    return header;
  }

  public BrandFooter footer() {
    return footer;
  }

  public static final class Builder {

    private final BrandId id;
    private final BrandIdentity identity;
    private BrandAssets assets = BrandAssets.empty();
    private BrandContactInformation contact = BrandContactInformation.of("", "", "");
    private BrandRegistrationInformation registration = BrandRegistrationInformation.of("", "");
    private BrandHeader header = BrandHeader.of("");
    private BrandFooter footer = BrandFooter.of("");

    private Builder(BrandId id, BrandIdentity identity) {
      this.id = id;
      this.identity = identity;
    }

    public Builder assets(BrandAssets assets) {
      this.assets = assets;
      return this;
    }

    public Builder contact(BrandContactInformation contact) {
      this.contact = contact;
      return this;
    }

    public Builder registration(BrandRegistrationInformation registration) {
      this.registration = registration;
      return this;
    }

    public Builder header(BrandHeader header) {
      this.header = header;
      return this;
    }

    public Builder footer(BrandFooter footer) {
      this.footer = footer;
      return this;
    }

    public BrandProfile build() {
      return new BrandProfile(this);
    }
  }
}
