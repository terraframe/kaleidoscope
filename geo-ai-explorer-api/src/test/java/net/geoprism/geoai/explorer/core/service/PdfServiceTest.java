package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class PdfServiceTest
{
  @Test
  public void testFromMarkdown()
  {
    String markdown = """
        ## National Economic Development (NED) Analysis

        The recommended plan has **net benefits** of $1.2M & a BCR of 2.4 < 3.

        | Plan | Cost |
        | ---- | ---- |
        | 4 - Nonstructural Plan | 2011 |
        | 7 - Low Scope Plan | 392.8 |

        <img src="http://169.254.169.254/latest/meta-data/">
        """;

    byte[] pdf = new PdfService().fromMarkdown("Plan Evaluation", markdown);

    assertTrue(pdf.length > 0);
    assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"));
  }
}
