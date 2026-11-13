package net.geoprism.geoai.explorer.core.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import net.geoprism.geoai.explorer.core.model.GenericRestException;

/**
 * Renders markdown to a PDF: markdown -> HTML (commonmark), parsed into a
 * well-formed DOM (jsoup, since openhtmltopdf needs XHTML), then laid out as
 * a PDF (openhtmltopdf).
 */
@Service
public class PdfService
{
  private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());

  private static final String          STYLES     = """
      @page { size: letter; margin: 1in; }
      body { font-family: serif; font-size: 11pt; line-height: 1.4; }
      h1 { font-size: 20pt; margin-bottom: 0.5em; }
      h2 { font-size: 15pt; margin-top: 1.5em; }
      h3 { font-size: 12pt; margin-top: 1.2em; }
      table { border-collapse: collapse; width: 100%; margin: 1em 0; -fs-table-paginate: paginate; }
      th, td { border: 1px solid #999; padding: 4px 6px; text-align: left; vertical-align: top; }
      th { background: #eee; }
      pre, code { font-family: monospace; font-size: 9pt; }
      pre { white-space: pre-wrap; }
      """;

  private final Parser                 parser     = Parser.builder().extensions(EXTENSIONS).build();

  /*
   * The markdown comes from an LLM, so raw HTML in it is escaped rather than
   * rendered, and unsafe link urls are stripped. Otherwise an <img> or <link>
   * in the agent's output would have the PDF renderer fetch arbitrary urls
   * from the server.
   */
  private final HtmlRenderer           renderer   = HtmlRenderer.builder() //
      .extensions(EXTENSIONS) //
      .escapeHtml(true) //
      .sanitizeUrls(true) //
      .build();

  public byte[] fromMarkdown(String title, String markdown)
  {
    String body = this.renderer.render(this.parser.parse(markdown));

    org.jsoup.nodes.Document html = Jsoup.parse("<html><head><meta charset=\"utf-8\"/></head><body></body></html>");
    html.title(title);
    html.head().appendElement("style").text(STYLES);
    html.body().appendElement("h1").text(title);
    html.body().append(body);

    try (ByteArrayOutputStream out = new ByteArrayOutputStream())
    {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withW3cDocument(new W3CDom().fromJsoup(html), null);
      builder.toStream(out);
      builder.run();

      return out.toByteArray();
    }
    catch (IOException e)
    {
      throw new GenericRestException("Unable to create the report PDF", e);
    }
  }
}
