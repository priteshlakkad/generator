package com.pdf.generator.service;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.pdf.generator.dto.design.BandDto;
import com.pdf.generator.dto.design.ElementDto;
import com.pdf.generator.dto.design.FieldInfo;
import com.pdf.generator.dto.design.ParameterInfo;
import com.pdf.generator.dto.design.ReadOnlyBandDto;
import com.pdf.generator.dto.design.ReportDesignDto;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRGroup;
import net.sf.jasperreports.engine.JRImage;
import net.sf.jasperreports.engine.JRLineBox;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRSection;
import net.sf.jasperreports.engine.JRStaticText;
import net.sf.jasperreports.engine.JRTextElement;
import net.sf.jasperreports.engine.JRTextField;
import net.sf.jasperreports.engine.base.JRBoxPen;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignImage;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignTextElement;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalImageAlignEnum;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.SplitTypeEnum;
import net.sf.jasperreports.engine.type.VerticalImageAlignEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;

@Service
public class ReportDesignService {

	public ReportDesignDto toDto(String templateType, JasperDesign design) {
		ReportDesignDto dto = new ReportDesignDto();
		dto.setTemplateType(templateType);
		dto.setPageWidth(design.getPageWidth());
		dto.setPageHeight(design.getPageHeight());
		dto.setOrientation(design.getOrientationValue().getName().toUpperCase(Locale.ROOT));
		dto.setColumnWidth(design.getColumnWidth());
		dto.setLeftMargin(design.getLeftMargin());
		dto.setRightMargin(design.getRightMargin());
		dto.setTopMargin(design.getTopMargin());
		dto.setBottomMargin(design.getBottomMargin());

		List<BandDto> bands = new ArrayList<>();
		List<ReadOnlyBandDto> readOnly = new ArrayList<>();

		describeBand("title", design.getTitle(), bands, readOnly);
		describeBand("pageHeader", design.getPageHeader(), bands, readOnly);
		describeBand("columnHeader", design.getColumnHeader(), bands, readOnly);
		describeBand("detail", firstBand(design.getDetailSection()), bands, readOnly);
		describeBand("columnFooter", design.getColumnFooter(), bands, readOnly);
		describeBand("pageFooter", design.getPageFooter(), bands, readOnly);
		describeBand("lastPageFooter", design.getLastPageFooter(), bands, readOnly);
		describeBand("summary", design.getSummary(), bands, readOnly);

		for (JRGroup group : design.getGroupsList()) {
			describeBand("groupHeader:" + group.getName(), firstBand(group.getGroupHeaderSection()), bands, readOnly);
			describeBand("groupFooter:" + group.getName(), firstBand(group.getGroupFooterSection()), bands, readOnly);
		}

		if (design.getBackground() != null) {
			readOnly.add(new ReadOnlyBandDto("background", "not editable in this version"));
		}
		if (design.getNoData() != null) {
			readOnly.add(new ReadOnlyBandDto("noData", "not editable in this version"));
		}

		dto.setBands(bands);
		dto.setReadOnlyBands(readOnly);

		List<ParameterInfo> parameters = new ArrayList<>();
		for (JRParameter parameter : design.getParametersList()) {
			if (parameter.isSystemDefined()) {
				continue;
			}
			parameters.add(new ParameterInfo(parameter.getName(), parameter.getValueClassName()));
		}
		dto.setParameters(parameters);

		List<FieldInfo> fields = new ArrayList<>();
		for (JRField field : design.getFieldsList()) {
			fields.add(new FieldInfo(field.getName(), field.getValueClassName()));
		}
		dto.setFields(fields);

		return dto;
	}

	public JasperDesign applyDesign(JasperDesign design, ReportDesignDto patch) {
		for (BandDto bandDto : patch.getBands()) {
			assignBand(design, bandDto.getName(), buildBand(design, bandDto));
		}
		return design;
	}

	public JasperDesign addParameter(JasperDesign design, String name, String className) {
		if (design.getParametersMap().containsKey(name)) {
			throw new IllegalArgumentException("Parameter '" + name + "' already exists");
		}
		JRDesignParameter parameter = new JRDesignParameter();
		parameter.setName(name);
		parameter.setValueClassName(className != null && !className.isBlank() ? className : "java.lang.String");
		try {
			design.addParameter(parameter);
		} catch (JRException e) {
			throw new IllegalArgumentException("Could not add parameter '" + name + "': " + e.getMessage(), e);
		}
		return design;
	}

	public JasperDesign removeParameter(JasperDesign design, String name) {
		if (design.removeParameter(name) == null) {
			throw new IllegalArgumentException("Parameter '" + name + "' does not exist");
		}
		return design;
	}

	public JasperDesign addField(JasperDesign design, String name, String className) {
		if (design.getFieldsMap().containsKey(name)) {
			throw new IllegalArgumentException("Field '" + name + "' already exists");
		}
		JRDesignField field = new JRDesignField();
		field.setName(name);
		field.setValueClassName(className != null && !className.isBlank() ? className : "java.lang.String");
		try {
			design.addField(field);
		} catch (JRException e) {
			throw new IllegalArgumentException("Could not add field '" + name + "': " + e.getMessage(), e);
		}
		return design;
	}

	public JasperDesign removeField(JasperDesign design, String name) {
		if (design.removeField(name) == null) {
			throw new IllegalArgumentException("Field '" + name + "' does not exist");
		}
		return design;
	}

	// ---- reading (JasperDesign -> DTO) ----

	private JRBand firstBand(JRSection section) {
		if (section == null) {
			return null;
		}
		JRBand[] sectionBands = section.getBands();
		return sectionBands.length > 0 ? sectionBands[0] : null;
	}

	private void describeBand(String name, JRBand band, List<BandDto> bands, List<ReadOnlyBandDto> readOnly) {
		if (band == null) {
			BandDto empty = new BandDto();
			empty.setName(name);
			empty.setHeight(0);
			bands.add(empty);
			return;
		}

		JRElement[] elements = band.getElements();
		for (JRElement element : elements) {
			if (!(element instanceof JRStaticText) && !(element instanceof JRTextField) && !(element instanceof JRImage)) {
				readOnly.add(new ReadOnlyBandDto(name, "contains a " + element.getClass().getSimpleName() + " element"));
				return;
			}
		}

		BandDto bandDto = new BandDto();
		bandDto.setName(name);
		bandDto.setHeight(band.getHeight());
		List<ElementDto> elementDtos = new ArrayList<>();
		for (int i = 0; i < elements.length; i++) {
			elementDtos.add(describeElement(elements[i], name, i));
		}
		bandDto.setElements(elementDtos);
		bands.add(bandDto);
	}

	private ElementDto describeElement(JRElement element, String bandName, int index) {
		ElementDto dto = new ElementDto();
		dto.setId(bandName + "-" + index);
		dto.setX(element.getX());
		dto.setY(element.getY());
		dto.setWidth(element.getWidth());
		dto.setHeight(element.getHeight());

		JRExpression printWhen = element.getPrintWhenExpression();
		if (printWhen != null) {
			dto.setPrintWhenExpression(printWhen.getText());
		}

		if (element instanceof JRStaticText staticText) {
			dto.setType("staticText");
			dto.setText(staticText.getText());
			describeTextStyle(dto, staticText);
			describeBorder(dto, staticText.getLineBox());
		} else if (element instanceof JRTextField textField) {
			dto.setType("textField");
			describeExpression(dto, textField.getExpression());
			describeTextStyle(dto, textField);
			describeBorder(dto, textField.getLineBox());
		} else if (element instanceof JRImage image) {
			dto.setType("image");
			describeExpression(dto, image.getExpression());
			if (image.getOwnHorizontalImageAlign() != null) {
				dto.setHorizontalAlign(image.getOwnHorizontalImageAlign().getName().toUpperCase(Locale.ROOT));
			}
			if (image.getOwnVerticalImageAlign() != null) {
				dto.setVerticalAlign(image.getOwnVerticalImageAlign().getName().toUpperCase(Locale.ROOT));
			}
			describeCommonStyle(dto, image.getOwnModeValue(), image.getOwnForecolor(), image.getOwnBackcolor());
			describeBorder(dto, image.getLineBox());
		}

		return dto;
	}

	private void describeExpression(ElementDto dto, JRExpression expression) {
		if (expression == null) {
			return;
		}
		dto.setExpression(expression.getText());
		dto.setExpressionClass(expression.getValueClassName());
	}

	private void describeTextStyle(ElementDto dto, JRTextElement element) {
		dto.setFontName(element.getOwnFontName());
		dto.setFontSize(element.getOwnFontsize());
		dto.setBold(element.isOwnBold());
		dto.setItalic(element.isOwnItalic());
		dto.setUnderline(element.isOwnUnderline());
		if (element.getOwnHorizontalTextAlign() != null) {
			dto.setHorizontalAlign(element.getOwnHorizontalTextAlign().getName().toUpperCase(Locale.ROOT));
		}
		if (element.getOwnVerticalTextAlign() != null) {
			dto.setVerticalAlign(element.getOwnVerticalTextAlign().getName().toUpperCase(Locale.ROOT));
		}
		describeCommonStyle(dto, element.getOwnModeValue(), element.getOwnForecolor(), element.getOwnBackcolor());
	}

	private void describeCommonStyle(ElementDto dto, ModeEnum mode, Color foreColor, Color backColor) {
		if (mode != null) {
			dto.setMode(mode.getName().toUpperCase(Locale.ROOT));
		}
		if (foreColor != null) {
			dto.setForeColor(toHex(foreColor));
		}
		if (backColor != null) {
			dto.setBackColor(toHex(backColor));
		}
	}

	private void describeBorder(ElementDto dto, JRLineBox lineBox) {
		if (lineBox == null) {
			return;
		}
		JRBoxPen pen = lineBox.getPen();
		Float width = pen.getOwnLineWidth();
		if (width != null && width > 0f) {
			dto.setBorder(true);
			dto.setBorderWidth(width);
			if (pen.getOwnLineColor() != null) {
				dto.setBorderColor(toHex(pen.getOwnLineColor()));
			}
		}
	}

	private String toHex(Color color) {
		return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
	}

	// ---- writing (DTO -> JasperDesign) ----

	private void assignBand(JasperDesign design, String name, JRDesignBand newBand) {
		boolean empty = newBand.getElements().length == 0 && newBand.getHeight() == 0;
		JRBand bandOrNull = empty ? null : newBand;

		switch (name) {
			case "title" -> design.setTitle(bandOrNull);
			case "pageHeader" -> design.setPageHeader(bandOrNull);
			case "columnHeader" -> design.setColumnHeader(bandOrNull);
			case "columnFooter" -> design.setColumnFooter(bandOrNull);
			case "pageFooter" -> design.setPageFooter(bandOrNull);
			case "lastPageFooter" -> design.setLastPageFooter(bandOrNull);
			case "summary" -> design.setSummary(bandOrNull);
			case "detail" -> replaceSectionBand((JRDesignSection) design.getDetailSection(), newBand, empty);
			default -> assignGroupBand(design, name, newBand, empty);
		}
	}

	private void assignGroupBand(JasperDesign design, String name, JRDesignBand newBand, boolean empty) {
		String groupName;
		boolean header;
		if (name.startsWith("groupHeader:")) {
			groupName = name.substring("groupHeader:".length());
			header = true;
		} else if (name.startsWith("groupFooter:")) {
			groupName = name.substring("groupFooter:".length());
			header = false;
		} else {
			throw new IllegalArgumentException("Unknown or unsupported band: " + name);
		}

		JRGroup group = design.getGroupsList().stream()
			.filter(g -> g.getName().equals(groupName))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown group: " + groupName));

		JRSection section = header ? group.getGroupHeaderSection() : group.getGroupFooterSection();
		replaceSectionBand((JRDesignSection) section, newBand, empty);
	}

	private void replaceSectionBand(JRDesignSection section, JRDesignBand newBand, boolean empty) {
		for (JRBand old : List.copyOf(section.getBandsList())) {
			section.removeBand(old);
		}
		if (!empty) {
			section.addBand(newBand);
		}
	}

	private JRDesignBand buildBand(JasperDesign design, BandDto bandDto) {
		JRDesignBand band = new JRDesignBand();
		band.setHeight(Math.max(bandDto.getHeight(), 0));
		band.setSplitType(SplitTypeEnum.STRETCH);
		int index = 0;
		for (ElementDto elementDto : bandDto.getElements()) {
			band.addElement(buildElement(design, elementDto, index++));
		}
		return band;
	}

	private JRDesignElement buildElement(JasperDesign design, ElementDto dto, int index) {
		JRDesignElement element = switch (dto.getType()) {
			case "staticText" -> buildStaticText(design, dto);
			case "textField" -> buildTextField(design, dto);
			case "image" -> buildImage(design, dto);
			default -> throw new IllegalArgumentException("Unsupported element type: " + dto.getType());
		};
		element.setX(dto.getX());
		element.setY(dto.getY());
		element.setWidth(dto.getWidth());
		element.setHeight(dto.getHeight());
		if (dto.getPrintWhenExpression() != null && !dto.getPrintWhenExpression().isBlank()) {
			element.setPrintWhenExpression(buildExpression(dto.getPrintWhenExpression(), "java.lang.Boolean"));
		}
		if (dto.getMode() != null) {
			element.setMode(ModeEnum.valueOf(dto.getMode()));
		}
		if (dto.getForeColor() != null) {
			element.setForecolor(fromHex(dto.getForeColor()));
		}
		if (dto.getBackColor() != null) {
			element.setBackcolor(fromHex(dto.getBackColor()));
		}
		return element;
	}

	private JRDesignStaticText buildStaticText(JasperDesign design, ElementDto dto) {
		JRDesignStaticText staticText = new JRDesignStaticText(design);
		staticText.setText(dto.getText() != null ? dto.getText() : "");
		applyTextStyle(staticText, dto);
		applyBorder(staticText.getLineBox(), dto);
		return staticText;
	}

	private JRDesignTextField buildTextField(JasperDesign design, ElementDto dto) {
		JRDesignTextField textField = new JRDesignTextField(design);
		textField.setExpression(buildExpression(dto.getExpression(), dto.getExpressionClass()));
		textField.setBlankWhenNull(true);
		applyTextStyle(textField, dto);
		applyBorder(textField.getLineBox(), dto);
		return textField;
	}

	private JRDesignImage buildImage(JasperDesign design, ElementDto dto) {
		JRDesignImage image = new JRDesignImage(design);
		image.setExpression(buildExpression(dto.getExpression(), dto.getExpressionClass()));
		if (dto.getHorizontalAlign() != null) {
			image.setHorizontalImageAlign(HorizontalImageAlignEnum.valueOf(dto.getHorizontalAlign()));
		}
		if (dto.getVerticalAlign() != null) {
			image.setVerticalImageAlign(VerticalImageAlignEnum.valueOf(dto.getVerticalAlign()));
		}
		applyBorder(image.getLineBox(), dto);
		return image;
	}

	private void applyTextStyle(JRDesignTextElement element, ElementDto dto) {
		if (dto.getFontName() != null) {
			element.setFontName(dto.getFontName());
		}
		if (dto.getFontSize() != null) {
			element.setFontSize(dto.getFontSize());
		}
		if (dto.getBold() != null) {
			element.setBold(dto.getBold());
		}
		if (dto.getItalic() != null) {
			element.setItalic(dto.getItalic());
		}
		if (dto.getUnderline() != null) {
			element.setUnderline(dto.getUnderline());
		}
		if (dto.getHorizontalAlign() != null) {
			element.setHorizontalTextAlign(HorizontalTextAlignEnum.valueOf(dto.getHorizontalAlign()));
		}
		if (dto.getVerticalAlign() != null) {
			element.setVerticalTextAlign(VerticalTextAlignEnum.valueOf(dto.getVerticalAlign()));
		}
	}

	private void applyBorder(JRLineBox lineBox, ElementDto dto) {
		if (!dto.isBorder()) {
			return;
		}
		JRBoxPen pen = lineBox.getPen();
		pen.setLineWidth(dto.getBorderWidth() != null ? dto.getBorderWidth() : 0.5f);
		pen.setLineColor(fromHex(dto.getBorderColor() != null ? dto.getBorderColor() : "#999999"));
	}

	private JRDesignExpression buildExpression(String text, String className) {
		JRDesignExpression expression = new JRDesignExpression();
		expression.setText(text != null && !text.isBlank() ? text : "null");
		expression.setValueClassName(className != null && !className.isBlank() ? className : "java.lang.String");
		return expression;
	}

	private Color fromHex(String hex) {
		return Color.decode(hex);
	}
}
