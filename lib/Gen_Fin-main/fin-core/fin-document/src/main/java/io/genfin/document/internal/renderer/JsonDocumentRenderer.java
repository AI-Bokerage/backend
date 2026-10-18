package io.genfin.document.internal.renderer;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RenderException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Renders a {@link ComposedDocument} as a single JSON object, preserving all model data. */
public final class JsonDocumentRenderer implements DocumentRenderer {

  private static final RendererId ID = RendererId.of("json");

  @Override
  public RendererId id() {
    return ID;
  }

  @Override
  public RendererCapabilities capabilities() {
    return () -> "application/json";
  }

  @Override
  public RenderResult render(ComposedDocument document, RendererConfiguration configuration)
      throws RenderException {
    StringBuilder json = new StringBuilder();
    json.append("{");
    json.append("\"documentId\":\"").append(escape(document.metadata().id().value())).append("\",");
    json.append("\"documentType\":\"")
        .append(escape(document.metadata().type().code()))
        .append("\",");
    json.append("\"locale\":").append(nullableString(document.metadata().locale())).append(",");
    json.append("\"currency\":").append(nullableString(document.metadata().currency())).append(",");
    json.append("\"createdAt\":")
        .append(
            document.metadata().createdAt() == null
                ? "null"
                : "\"" + escape(document.metadata().createdAt().toString()) + "\"")
        .append(",");
    json.append("\"attributes\":")
        .append(attributesJson(document.attributes().asMap()))
        .append(",");
    json.append("\"sections\":[");

    ElementJsonVisitor visitor = new ElementJsonVisitor();
    for (int i = 0; i < document.sections().size(); i++) {
      DocumentSection section = document.sections().get(i);
      json.append("{\"title\":\"").append(escape(section.title())).append("\",\"elements\":[");
      for (int j = 0; j < section.elements().size(); j++) {
        json.append(section.elements().get(j).accept(visitor));
        if (j < section.elements().size() - 1) {
          json.append(",");
        }
      }
      json.append("]}");
      if (i < document.sections().size() - 1) {
        json.append(",");
      }
    }
    json.append("]}");

    return RenderResult.of(
        id(), json.toString().getBytes(StandardCharsets.UTF_8), capabilities().mimeType());
  }

  private static String nullableString(String value) {
    return value == null ? "null" : "\"" + escape(value) + "\"";
  }

  private static String attributesJson(Map<String, String> attributes) {
    StringBuilder attrs = new StringBuilder("{");
    int index = 0;
    for (var entry : attributes.entrySet()) {
      if (index > 0) {
        attrs.append(",");
      }
      attrs
          .append("\"")
          .append(escape(entry.getKey()))
          .append("\":\"")
          .append(escape(entry.getValue()))
          .append("\"");
      index++;
    }
    attrs.append("}");
    return attrs.toString();
  }

  private static String tableJson(TableElement element) {
    StringBuilder table = new StringBuilder("{\"headers\":[");
    for (int i = 0; i < element.headers().size(); i++) {
      if (i > 0) {
        table.append(",");
      }
      table.append("\"").append(escape(element.headers().get(i))).append("\"");
    }
    table.append("],\"rows\":[");
    for (int i = 0; i < element.rows().size(); i++) {
      if (i > 0) {
        table.append(",");
      }
      table.append("[");
      var row = element.rows().get(i);
      for (int j = 0; j < row.size(); j++) {
        if (j > 0) {
          table.append(",");
        }
        table.append("\"").append(escape(row.get(j))).append("\"");
      }
      table.append("]");
    }
    table.append("]}");
    return table.toString();
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private static final class ElementJsonVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "{\"label\":\""
          + escape(element.label())
          + "\",\"value\":\""
          + escape(element.value())
          + "\"}";
    }

    @Override
    public String visitTable(TableElement element) {
      return "{\"table\":" + tableJson(element) + "}";
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return "{\"text\":\"" + escape(element.text()) + "\"}";
    }
  }
}
