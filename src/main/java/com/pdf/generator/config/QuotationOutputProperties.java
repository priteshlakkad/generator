package com.pdf.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Filesystem destination for PDFs created through the generation REST endpoint. */
@ConfigurationProperties(prefix = "pdf.output")
public class QuotationOutputProperties {

    /** Root beneath which generation date, template and quotation folders are created. */
    private String dir = "./output/quotations";

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }
}
