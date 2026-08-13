package com.pdf.generator.dto.design;

import java.util.ArrayList;
import java.util.List;

public class BandDto {

	private String name;
	private int height;
	private List<ElementDto> elements = new ArrayList<>();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public List<ElementDto> getElements() {
		return elements;
	}

	public void setElements(List<ElementDto> elements) {
		this.elements = elements;
	}
}
