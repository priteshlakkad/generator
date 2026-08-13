package com.pdf.generator.dto.design;

import java.util.ArrayList;
import java.util.List;

public class ReportDesignDto {

	private String templateType;
	private int pageWidth;
	private int pageHeight;
	private String orientation;
	private int columnWidth;
	private int leftMargin;
	private int rightMargin;
	private int topMargin;
	private int bottomMargin;
	private List<BandDto> bands = new ArrayList<>();
	private List<ReadOnlyBandDto> readOnlyBands = new ArrayList<>();
	private List<ParameterInfo> parameters = new ArrayList<>();
	private List<FieldInfo> fields = new ArrayList<>();

	public String getTemplateType() {
		return templateType;
	}

	public void setTemplateType(String templateType) {
		this.templateType = templateType;
	}

	public int getPageWidth() {
		return pageWidth;
	}

	public void setPageWidth(int pageWidth) {
		this.pageWidth = pageWidth;
	}

	public int getPageHeight() {
		return pageHeight;
	}

	public void setPageHeight(int pageHeight) {
		this.pageHeight = pageHeight;
	}

	public String getOrientation() {
		return orientation;
	}

	public void setOrientation(String orientation) {
		this.orientation = orientation;
	}

	public int getColumnWidth() {
		return columnWidth;
	}

	public void setColumnWidth(int columnWidth) {
		this.columnWidth = columnWidth;
	}

	public int getLeftMargin() {
		return leftMargin;
	}

	public void setLeftMargin(int leftMargin) {
		this.leftMargin = leftMargin;
	}

	public int getRightMargin() {
		return rightMargin;
	}

	public void setRightMargin(int rightMargin) {
		this.rightMargin = rightMargin;
	}

	public int getTopMargin() {
		return topMargin;
	}

	public void setTopMargin(int topMargin) {
		this.topMargin = topMargin;
	}

	public int getBottomMargin() {
		return bottomMargin;
	}

	public void setBottomMargin(int bottomMargin) {
		this.bottomMargin = bottomMargin;
	}

	public List<BandDto> getBands() {
		return bands;
	}

	public void setBands(List<BandDto> bands) {
		this.bands = bands;
	}

	public List<ReadOnlyBandDto> getReadOnlyBands() {
		return readOnlyBands;
	}

	public void setReadOnlyBands(List<ReadOnlyBandDto> readOnlyBands) {
		this.readOnlyBands = readOnlyBands;
	}

	public List<ParameterInfo> getParameters() {
		return parameters;
	}

	public void setParameters(List<ParameterInfo> parameters) {
		this.parameters = parameters;
	}

	public List<FieldInfo> getFields() {
		return fields;
	}

	public void setFields(List<FieldInfo> fields) {
		this.fields = fields;
	}
}
