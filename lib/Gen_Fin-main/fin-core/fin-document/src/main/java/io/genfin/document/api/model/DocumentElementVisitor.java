package io.genfin.document.api.model;

public interface DocumentElementVisitor<R> {
  R visitKeyValue(KeyValueElement element);

  R visitTable(TableElement element);

  R visitTextBlock(TextBlockElement element);
}
