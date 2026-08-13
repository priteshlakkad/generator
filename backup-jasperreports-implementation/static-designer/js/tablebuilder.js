const TableBuilder = (() => {

	let columns = [];

	function defaultColumn(index) {
		return { label: 'Column ' + index, fieldName: '', width: 100, align: 'LEFT', isNew: true };
	}

	function open(state, rerenderAll) {
		columns = [defaultColumn(1), defaultColumn(2)];
		render(state, rerenderAll);
		document.getElementById('tableBuilderOverlay').classList.remove('hidden');
	}

	function close() {
		document.getElementById('tableBuilderOverlay').classList.add('hidden');
	}

	function bandSelect(state, selectedName) {
		const select = document.createElement('select');
		state.design.bands.forEach((b) => {
			const opt = document.createElement('option');
			opt.value = b.name;
			opt.textContent = b.name;
			if (b.name === selectedName) opt.selected = true;
			select.appendChild(opt);
		});
		return select;
	}

	function render(state, rerenderAll) {
		const body = document.getElementById('tableBuilderBody');
		body.innerHTML = '';

		const bandRow = document.createElement('div');
		bandRow.className = 'field-row';
		const headerField = document.createElement('div');
		headerField.className = 'field';
		headerField.innerHTML = '<label>Header band</label>';
		const headerSelect = bandSelect(state, 'columnHeader');
		headerField.appendChild(headerSelect);
		const dataField = document.createElement('div');
		dataField.className = 'field';
		dataField.innerHTML = '<label>Data band</label>';
		const dataSelect = bandSelect(state, 'detail');
		dataField.appendChild(dataSelect);
		bandRow.appendChild(headerField);
		bandRow.appendChild(dataField);
		body.appendChild(bandRow);

		const columnsContainer = document.createElement('div');
		columnsContainer.id = 'tableBuilderColumns';
		body.appendChild(columnsContainer);
		renderColumns(columnsContainer, state);

		const addColBtn = document.createElement('button');
		addColBtn.textContent = '+ Add Column';
		addColBtn.addEventListener('click', () => {
			columns.push(defaultColumn(columns.length + 1));
			renderColumns(columnsContainer, state);
		});
		body.appendChild(addColBtn);

		const actions = document.createElement('div');
		actions.className = 'builder-actions';
		const cancelBtn = document.createElement('button');
		cancelBtn.textContent = 'Cancel';
		cancelBtn.addEventListener('click', close);
		const insertBtn = document.createElement('button');
		insertBtn.className = 'primary';
		insertBtn.textContent = 'Insert Table';
		insertBtn.addEventListener('click', () => insert(state, rerenderAll, headerSelect.value, dataSelect.value));
		actions.appendChild(cancelBtn);
		actions.appendChild(insertBtn);
		body.appendChild(actions);
	}

	function renderColumns(container, state) {
		container.innerHTML = '';
		const existingFieldNames = state.design.fields.map((f) => f.name);

		columns.forEach((col, index) => {
			const row = document.createElement('div');
			row.className = 'builder-row';

			const labelInput = document.createElement('input');
			labelInput.type = 'text';
			labelInput.placeholder = 'Column label';
			labelInput.value = col.label;
			labelInput.addEventListener('change', () => { col.label = labelInput.value; });

			const fieldSelect = document.createElement('select');
			const newOpt = document.createElement('option');
			newOpt.value = '';
			newOpt.textContent = '-- new field --';
			fieldSelect.appendChild(newOpt);
			existingFieldNames.forEach((name) => {
				const opt = document.createElement('option');
				opt.value = name;
				opt.textContent = name;
				fieldSelect.appendChild(opt);
			});
			fieldSelect.value = col.isNew ? '' : col.fieldName;

			const fieldNameInput = document.createElement('input');
			fieldNameInput.type = 'text';
			fieldNameInput.placeholder = 'new field name';
			fieldNameInput.value = col.isNew ? col.fieldName : '';
			fieldNameInput.classList.toggle('hidden', !col.isNew);

			fieldSelect.addEventListener('change', () => {
				col.isNew = fieldSelect.value === '';
				col.fieldName = fieldSelect.value;
				fieldNameInput.classList.toggle('hidden', !col.isNew);
				fieldNameInput.value = '';
			});
			fieldNameInput.addEventListener('change', () => { col.fieldName = fieldNameInput.value.trim(); });

			const widthInput = document.createElement('input');
			widthInput.type = 'number';
			widthInput.value = col.width;
			widthInput.addEventListener('change', () => { col.width = parseInt(widthInput.value, 10) || 60; });

			const alignSelect = document.createElement('select');
			['LEFT', 'CENTER', 'RIGHT'].forEach((a) => {
				const opt = document.createElement('option');
				opt.value = a;
				opt.textContent = a;
				if (a === col.align) opt.selected = true;
				alignSelect.appendChild(opt);
			});
			alignSelect.addEventListener('change', () => { col.align = alignSelect.value; });

			const removeBtn = document.createElement('button');
			removeBtn.className = 'remove-row';
			removeBtn.textContent = '×';
			removeBtn.addEventListener('click', () => {
				columns.splice(index, 1);
				renderColumns(container, state);
			});

			const fieldCell = document.createElement('div');
			fieldCell.appendChild(fieldSelect);
			fieldCell.appendChild(fieldNameInput);

			row.appendChild(labelInput);
			row.appendChild(fieldCell);
			row.appendChild(widthInput);
			row.appendChild(alignSelect);
			row.appendChild(removeBtn);
			container.appendChild(row);
		});
	}

	async function insert(state, rerenderAll, headerBandName, dataBandName) {
		const headerBand = state.design.bands.find((b) => b.name === headerBandName);
		const dataBand = state.design.bands.find((b) => b.name === dataBandName);
		if (!headerBand || !dataBand) {
			alert('Could not find the selected bands.');
			return;
		}

		let x = 0;
		for (const col of columns) {
			if (!col.fieldName) continue;
			if (col.isNew) {
				try {
					await Api.addField(state.templateType, col.fieldName, 'java.lang.String');
					state.design.fields.push({ name: col.fieldName, className: 'java.lang.String' });
				} catch (err) {
					alert('Could not create field "' + col.fieldName + '": ' + err.message);
					return;
				}
			}

			headerBand.elements.push({
				type: 'staticText', x, y: 0, width: col.width, height: 20,
				text: col.label, bold: true, horizontalAlign: col.align, verticalAlign: 'MIDDLE',
				border: true, mode: 'OPAQUE', backColor: '#D9D9D9'
			});
			dataBand.elements.push({
				type: 'textField', x, y: 0, width: col.width, height: 20,
				expression: '$F{' + col.fieldName + '}', expressionClass: 'java.lang.String',
				horizontalAlign: col.align, verticalAlign: 'MIDDLE', border: true
			});

			headerBand.height = Math.max(headerBand.height, 20);
			dataBand.height = Math.max(dataBand.height, 20);
			x += col.width;
		}

		close();
		rerenderAll();
	}

	return { open, close };
})();
