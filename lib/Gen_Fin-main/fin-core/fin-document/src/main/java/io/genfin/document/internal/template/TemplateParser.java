package io.genfin.document.internal.template;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.placeholder.Placeholder;
import io.genfin.document.api.template.EachFragment;
import io.genfin.document.api.template.IfFragment;
import io.genfin.document.api.template.IncludeFragment;
import io.genfin.document.api.template.LiteralFragment;
import io.genfin.document.api.template.PlaceholderFragment;
import io.genfin.document.api.template.TemplateFragment;
import io.genfin.document.api.template.TemplateSection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hand-rolled tokenizer/parser for the {@code {{...}}} template syntax: bare placeholders, {@code
 * {{#if cond}}...{{/if}}}, {@code {{#each coll}}...{{/each}}}, {@code {{#section
 * "name"}}...{{/section}}} and {@code {{include "id"}}}. No external templating library is used.
 */
final class TemplateParser {

  private static final String IF_OPEN_PREFIX = "#if ";
  private static final String EACH_OPEN_PREFIX = "#each ";
  private static final String SECTION_OPEN_PREFIX = "#section ";
  private static final String INCLUDE_PREFIX = "include ";
  private static final String IF_CLOSE = "/if";
  private static final String EACH_CLOSE = "/each";
  private static final String SECTION_CLOSE = "/section";

  private TemplateParser() {}

  static ParsedTemplate parse(String rawSource) {
    if (rawSource == null) {
      throw new ValidationException("Template source must not be null");
    }
    List<Token> tokens = tokenize(rawSource);
    Parser parser = new Parser(tokens);
    List<TemplateFragment> topLevelFragments = parser.parseUntil(null);
    return new ParsedTemplate(topLevelFragments, parser.sections, parser.sectionPositions);
  }

  private static List<Token> tokenize(String source) {
    List<Token> tokens = new ArrayList<>();
    int pos = 0;
    int length = source.length();
    while (pos < length) {
      int open = source.indexOf("{{", pos);
      if (open < 0) {
        tokens.add(Token.literal(source.substring(pos)));
        break;
      }
      if (open > pos) {
        tokens.add(Token.literal(source.substring(pos, open)));
      }
      int close = source.indexOf("}}", open + 2);
      if (close < 0) {
        throw new ValidationException(
            "Malformed template: unmatched '{{' delimiter at position " + open);
      }
      tokens.add(Token.tag(source.substring(open + 2, close).trim()));
      pos = close + 2;
    }
    return tokens;
  }

  private static String unquote(String raw) {
    if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
      return raw.substring(1, raw.length() - 1);
    }
    return raw;
  }

  /** Immutable token: either a literal text run or a raw (trimmed) {{...}} tag body. */
  private static final class Token {
    private final boolean tag;
    private final String text;

    private Token(boolean tag, String text) {
      this.tag = tag;
      this.text = text;
    }

    static Token literal(String text) {
      return new Token(false, text);
    }

    static Token tag(String text) {
      return new Token(true, text);
    }
  }

  /** Recursive-descent walk over the flat token list, accumulating extracted sections. */
  private static final class Parser {
    private final List<Token> tokens;
    private final List<TemplateSection> sections = new ArrayList<>();
    private final List<Integer> sectionPositions = new ArrayList<>();
    private final Set<String> sectionNames = new HashSet<>();
    private int pos;
    private int depth;

    Parser(List<Token> tokens) {
      this.tokens = tokens;
    }

    List<TemplateFragment> parseUntil(String closingTag) {
      List<TemplateFragment> body = new ArrayList<>();
      while (pos < tokens.size()) {
        Token token = tokens.get(pos);
        if (!token.tag) {
          body.add(LiteralFragment.of(token.text));
          pos++;
          continue;
        }

        String content = token.text;
        if (closingTag != null && closingTag.equals(content)) {
          pos++;
          return body;
        }
        if (IF_CLOSE.equals(content)
            || EACH_CLOSE.equals(content)
            || SECTION_CLOSE.equals(content)) {
          throw new ValidationException("Unmatched closing tag '{{" + content + "}}' in template");
        }
        if (content.startsWith(IF_OPEN_PREFIX)) {
          pos++;
          String key = content.substring(IF_OPEN_PREFIX.length()).trim();
          depth++;
          List<TemplateFragment> ifBody = parseUntil(IF_CLOSE);
          depth--;
          body.add(IfFragment.of(Placeholder.of(key), ifBody));
          continue;
        }
        if (content.startsWith(EACH_OPEN_PREFIX)) {
          pos++;
          String key = content.substring(EACH_OPEN_PREFIX.length()).trim();
          depth++;
          List<TemplateFragment> eachBody = parseUntil(EACH_CLOSE);
          depth--;
          body.add(EachFragment.of(Placeholder.of(key), eachBody));
          continue;
        }
        if (content.startsWith(SECTION_OPEN_PREFIX)) {
          pos++;
          String name = unquote(content.substring(SECTION_OPEN_PREFIX.length()).trim());
          if (!sectionNames.add(name)) {
            throw new ValidationException(
                "Duplicate section name '" + name + "' declared more than once in template");
          }
          // Record where in the enclosing top-level fragment stream this section occurred (or -1
          // if nested inside an if/each) so the compiler can splice its resolved content back
          // into its original document position instead of hoisting all sections to the front.
          int position = depth == 0 ? body.size() : -1;
          depth++;
          List<TemplateFragment> sectionBody = parseUntil(SECTION_CLOSE);
          depth--;
          sections.add(TemplateSection.of(name, sectionBody));
          sectionPositions.add(position);
          continue;
        }
        if (content.startsWith(INCLUDE_PREFIX)) {
          pos++;
          String id = unquote(content.substring(INCLUDE_PREFIX.length()).trim());
          body.add(IncludeFragment.of(TemplateId.of(id)));
          continue;
        }

        if (content.startsWith("#") || content.startsWith("/")) {
          throw new ValidationException("Unknown template directive: {{" + content + "}}");
        }

        pos++;
        body.add(PlaceholderFragment.of(Placeholder.of(content)));
      }

      if (closingTag != null) {
        throw new ValidationException(
            "Malformed template: missing closing '{{/" + closingTag.substring(1) + "}}' tag");
      }
      return body;
    }
  }
}
