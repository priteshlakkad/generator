const Inspector = (() => {

	const EXPRESSION_CLASSES = [
		'java.lang.String', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Double',
		'java.math.BigDecimal', 'java.lang.Boolean', 'java.util.Date'
	];

	function field(labelText, inputEl) {
		const wrap = document.createElement('div');
		wrap.className = 'field';
		const label = document.createElement('label');
		label.textContent = labelText;
		wrap.appendChild(label);
		wrap.appendChild(inputEl);
		return wrap;
	}

	function numberInput(value, onChange) {
		const input = document.createElement('input');
		input.type = 'number';
		input.value = value;
		input.addEventListener('change', () => onChange(parseInt(input.value, 10) || 0));
		return input;
	}

	function textInput(value, onChange) {
		const input = document.createElement('input');
		input.type = 'text';
		input.value = value || '';
		input.addEventListener('change', () => onChange(input.value));
		return input;
	}

	function textArea(value, onChange, onFocusRef) {
		const input = document.createElement('textarea');
		input.rows = 3;
		input.value = value || '';
		input.addEventListener('change', () => onChange(input.value));
		if (onFocusRef) onFocusRef(input);
		return input;
	}

	function selectInput(options, value, onChange) {
		const select = document.createElement('select');
		for (const opt of options) {
			const optionEl = document.createElement('option');
			optionEl.value = opt;
			optionEl.textContent = opt;
			if (opt === value) optionEl.selected = true;
			select.appendChild(optionEl);
		}
		select.addEventListener('change', () => onChange(select.value || null));
		return select;
	}

	function checkbox(checked, onChange, labelText) {
		const row = document.createElement('div');
		row.className = 'checkbox-row';
		const input = document.createElement('input');
		input.type = 'checkbox';
		input.checked = !!checked;
		input.id = 'chk-' + labelText.replace(/\s+/g, '-') + '-' + Math.random().toString(36).slice(2, 7);
		input.addEventListener('change', () => onChange(input.checked));
		const label = document.createElement('label');
		label.setAttribute('for', input.id);
		label.textContent = labelText;
		row.appendChild(input);
		row.appendChild(label);
		return row;
	}

	function tokenList(container, design, activeTextarea) {
		const wrap = document.createElement('div');
		wrap.className = 'token-list';
		const insert = (token) => {
			if (!activeTextarea) return;
			const start = activeTextarea.selectionStart || activeTextarea.value.length;
			const end = activeTextarea.selectionEnd || activeTextarea.value.length;
			activeTextarea.value = activeTextarea.value.slice(0, start) + token + activeTextarea.value.slice(end);
			activeTextarea.dispatchEvent(new Event('change'));
			activeTextarea.focus();
		};
		design.parameters.forEach((p) => {
			const chip = document.createElement('span');
			chip.className = 'token';
			chip.textContent = 'P: ' + p.name;
			chip.title = p.className;
			chip.addEventListener('click', () => insert(`$P{${p.name}}`));
			wrap.appendChild(chip);
		});
		design.fields.forEach((f) => {
			const chip = document.createElement('span');
			chip.className = 'token';
			chip.textContent = 'F: ' + f.name;
			chip.title = f.className;
			chip.addEventListener('click', () => insert(`$F{${f.name}}`));
			wrap.appendChild(chip);
		});
		container.appendChild(wrap);
	}

	function render(container, state, rerenderAll) {
		container.innerHTML = '';
		const heading = document.createElement('h3');
		heading.textContent = 'Properties';
		container.appendChild(heading);

		if (!state.selected) {
			const empty = document.createElement('div');
			empty.className = 'empty';
			empty.textContent = 'Select an element on the canvas, or drag one in from the palette.';
			container.appendChild(empty);
			return;
		}

		const band = state.design.bands.find((b) => b.name === state.selected.bandName);
		const el = band && band.elements[state.selected.index];
		if (!el) {
			state.selected = null;
			render(container, state, rerenderAll);
			return;
		}

		const typeLabel = document.createElement('div');
		typeLabel.className = 'template-meta';
		typeLabel.textContent = `${el.type} in "${band.name}"`;
		container.appendChild(typeLabel);

		const geometryRow = document.createElement('div');
		geometryRow.className = 'field-row';
		geometryRow.appendChild(field('X', numberInput(el.x, (v) => { el.x = v; rerenderAll(); })));
		geometryRow.appendChild(field('Y', numberInput(el.y, (v) => { el.y = v; rerenderAll(); })));
		container.appendChild(geometryRow);

		const sizeRow = document.createElement('div');
		sizeRow.className = 'field-row';
		sizeRow.appendChild(field('Width', numberInput(el.width, (v) => { el.width = Math.max(1, v); rerenderAll(); })));
		sizeRow.appendChild(field('Height', numberInput(el.height, (v) => { el.height = Math.max(1, v); rerenderAll(); })));
		container.appendChild(sizeRow);

		let expressionTextarea = null;

		if (el.type === 'staticText') {
			container.appendChild(field('Text', textArea(el.text, (v) => { el.text = v; rerenderAll(); })));
		} else {
			const ta = textArea(el.expression, (v) => { el.expression = v; rerenderAll(); }, (input) => { expressionTextarea = input; });
			container.appendChild(field('Expression', ta));
			container.appendChild(field('Expression type', selectInput(EXPRESSION_CLASSES, el.expressionClass, (v) => { el.expressionClass = v; })));
			tokenList(container, state.design, ta);
		}

		if (el.type === 'staticText' || el.type === 'textField') {
			const fontRow = document.createElement('div');
			fontRow.className = 'field-row';
			fontRow.appendChild(field('Font', textInput(el.fontName, (v) => { el.fontName = v || null; rerenderAll(); })));
			fontRow.appendChild(field('Size', numberInput(el.fontSize || 10, (v) => { el.fontSize = v; })));
			container.appendChild(fontRow);

			container.appendChild(checkbox(el.bold, (v) => { el.bold = v; }, 'Bold'));
			container.appendChild(checkbox(el.italic, (v) => { el.italic = v; }, 'Italic'));
			container.appendChild(checkbox(el.underline, (v) => { el.underline = v; }, 'Underline'));

			const alignRow = document.createElement('div');
			alignRow.className = 'field-row';
			alignRow.appendChild(field('H-Align', selectInput(['', 'LEFT', 'CENTER', 'RIGHT', 'JUSTIFIED'], el.horizontalAlign, (v) => { el.horizontalAlign = v || null; })));
			alignRow.appendChild(field('V-Align', selectInput(['', 'TOP', 'MIDDLE', 'BOTTOM', 'JUSTIFIED'], el.verticalAlign, (v) => { el.verticalAlign = v || null; })));
			container.appendChild(alignRow);
		}

		if (el.type === 'image') {
			const alignRow = document.createElement('div');
			alignRow.className = 'field-row';
			alignRow.appendChild(field('H-Align', selectInput(['', 'LEFT', 'CENTER', 'RIGHT'], el.horizontalAlign, (v) => { el.horizontalAlign = v || null; })));
			alignRow.appendChild(field('V-Align', selectInput(['', 'TOP', 'MIDDLE', 'BOTTOM'], el.verticalAlign, (v) => { el.verticalAlign = v || null; })));
			container.appendChild(alignRow);
		}

		container.appendChild(field('Print when (optional)', textInput(el.printWhenExpression, (v) => { el.printWhenExpression = v || null; })));

		const colorRow = document.createElement('div');
		colorRow.className = 'field-row';
		colorRow.appendChild(field('Text/line color', textInput(el.foreColor, (v) => { el.foreColor = v || null; rerenderAll(); })));
		colorRow.appendChild(field('Background color', textInput(el.backColor, (v) => { el.backColor = v || null; rerenderAll(); })));
		container.appendChild(colorRow);
		container.appendChild(field('Mode', selectInput(['', 'OPAQUE', 'TRANSPARENT'], el.mode, (v) => { el.mode = v || null; })));

		container.appendChild(checkbox(el.border, (v) => { el.border = v; render(container, state, rerenderAll); }, 'Show border'));
		if (el.border) {
			const borderRow = document.createElement('div');
			borderRow.className = 'field-row';
			borderRow.appendChild(field('Border width', numberInput(el.borderWidth || 0.5, (v) => { el.borderWidth = v; })));
			borderRow.appendChild(field('Border color', textInput(el.borderColor || '#999999', (v) => { el.borderColor = v; })));
			container.appendChild(borderRow);
		}

		const deleteBtn = document.createElement('button');
		deleteBtn.className = 'danger';
		deleteBtn.textContent = 'Delete element';
		deleteBtn.addEventListener('click', () => {
			band.elements.splice(state.selected.index, 1);
			state.selected = null;
			rerenderAll();
		});
		container.appendChild(deleteBtn);
	}

	function renderDataList(container, title, items, onAdd, onRemove) {
		const section = document.createElement('div');
		section.className = 'data-section';
		const heading = document.createElement('h3');
		heading.textContent = title;
		section.appendChild(heading);

		const list = document.createElement('ul');
		list.className = 'data-list';
		items.forEach((item) => {
			const li = document.createElement('li');
			const label = document.createElement('span');
			label.textContent = item.name;
			label.title = item.className;
			const removeBtn = document.createElement('button');
			removeBtn.className = 'remove';
			removeBtn.textContent = '×';
			removeBtn.title = 'Remove';
			removeBtn.addEventListener('click', () => onRemove(item.name));
			li.appendChild(label);
			li.appendChild(removeBtn);
			list.appendChild(li);
		});
		section.appendChild(list);

		const form = document.createElement('div');
		form.className = 'add-data-form';
		const nameInput = document.createElement('input');
		nameInput.type = 'text';
		nameInput.placeholder = 'name';
		const classSelect = document.createElement('select');
		EXPRESSION_CLASSES.forEach((cls) => {
			const opt = document.createElement('option');
			opt.value = cls;
			opt.textContent = cls.substring(cls.lastIndexOf('.') + 1);
			classSelect.appendChild(opt);
		});
		const addBtn = document.createElement('button');
		addBtn.textContent = 'Add';
		addBtn.addEventListener('click', () => {
			const name = nameInput.value.trim();
			if (!name) return;
			onAdd(name, classSelect.value);
		});
		form.appendChild(nameInput);
		form.appendChild(classSelect);
		form.appendChild(addBtn);
		section.appendChild(form);

		container.appendChild(section);
	}

	function renderDataPanel(container, state, rerenderAll) {
		container.innerHTML = '';

		renderDataList(container, 'Parameters', state.design.parameters,
			(name, className) => {
				Api.addParameter(state.templateType, name, className)
					.then(() => {
						state.design.parameters.push({ name, className });
						rerenderAll();
					})
					.catch((err) => alert('Could not add parameter: ' + err.message));
			},
			(name) => {
				Api.deleteParameter(state.templateType, name)
					.then(() => {
						state.design.parameters = state.design.parameters.filter((p) => p.name !== name);
						rerenderAll();
					})
					.catch((err) => alert('Could not remove parameter: ' + err.message));
			});

		renderDataList(container, 'Fields', state.design.fields,
			(name, className) => {
				Api.addField(state.templateType, name, className)
					.then(() => {
						state.design.fields.push({ name, className });
						rerenderAll();
					})
					.catch((err) => alert('Could not add field: ' + err.message));
			},
			(name) => {
				Api.deleteField(state.templateType, name)
					.then(() => {
						state.design.fields = state.design.fields.filter((f) => f.name !== name);
						rerenderAll();
					})
					.catch((err) => alert('Could not remove field: ' + err.message));
			});
	}

	return { render, renderDataPanel };
})();
