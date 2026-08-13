package com.pdf.generator.service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.pdf.generator.dto.ReportDataRequest;
import com.pdf.generator.exception.PdfGenerationException;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

@Service
public class PdfGenerationService {

	private final TemplateStorageService templateStorageService;

	public PdfGenerationService(TemplateStorageService templateStorageService) {
		this.templateStorageService = templateStorageService;
	}

	public byte[] generate(String templateType, ReportDataRequest request) {
		JasperReport report = templateStorageService.loadCompiledReport(templateType);

		Collection<Map<String, ?>> rows = new ArrayList<>(request.getData());
		JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(rows);

		try {
			JasperPrint print = JasperFillManager.fillReport(report, request.getParameters(), dataSource);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			JasperExportManager.exportReportToPdfStream(print, out);
			return out.toByteArray();
		} catch (JRException e) {
			throw new PdfGenerationException("Failed to generate PDF for template '" + templateType + "': " + e.getMessage(), e);
		}
	}
}
