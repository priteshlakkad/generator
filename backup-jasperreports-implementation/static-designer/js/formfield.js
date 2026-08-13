const FormFieldBuilder = (() => {

	let rows = [];

	function defaultRow(index) {
		return { label: 'Field ' + index, bindType: 'parameter', name: '', isNew: true };
	}

	function open(state, rerenderAll) {
		rows = [defaultRow(1), defaultRow(2)];
		render(state, rerenderAll);
		document.getElementById('formFieldOverlay').classList.remove('hidden');
	}

	function close() {
		document.getElementById('formFieldOverlay').classList.add('hidden');
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
		const body = document.getElementById('formFieldBody');
		body.innerHTML = '';

		const settingsRow = document.createElement('div');
		settingsRow.className = 'field-row';
		const bandField = document.createElement('div');
		bandField.className = 'field';
		bandField.innerHTML = '<label>Target band</label>';
		const bandSel = bandSelect(state, 'title');
		bandField.appendChild(bandSel);
		const perRowField = document.createElement('div');
		perRowField.className = 'field';
		perRowField.innerHTML = '<label>Fields per row</label>';
		const perRowSel = document.createElement('select');
		[1, 2, 3].forEach((n) => {
			const opt = document.createElement('option');
			opt.value = n;
			opt.textContent = n;
			if (n === 2) opt.selected = true;
			perRowSel.appendChild(opt);
		});
		perRowField.appendChild(perRowSel);
		settingsRow.appendChild(bandField);
		settingsRow.appendChild(perRowField);
		body.appendChild(settingsRow);

		const rowsContainer = document.createElement('div');
		rowsContainer.id = 'formFieldRows';
		body.appendChild(rowsContainer);
		renderRows(rowsContainer, state);

		const addRowBtn = document.createElement('button');
		addRowBtn.textContent = '+ Add Field';
		addRowBtn.addEventListener('click', () => {
			rows.push(defaultRow(rows.length + 1));
			renderRows(rowsContainer, state);
		});
		body.appendChild(addRowBtn);

		const actions = document.createElement('div');
		actions.className = 'builder-actions';
		const cancelBtn = document.createElement('button');
		cancelBtn.textContent = 'Cancel';
		cancelBtn.addEventListener('click', close);
		const insertBtn = document.createElement('button');
		insertBtn.className = 'primary';
		insertBtn.textContent = 'Insert Fields';
		insertBtn.addEventListener('click', () => insert(state, rerenderAll, bandSel.value, parseInt(perRowSel.value, 10)));
		actions.appendChild(cancelBtn);
		actions.appendChild(insertBtn);
		body.appendChild(actions);
	}

	function renderRows(container, state) {
		container.innerHTML = '';

		rows.forEach((row, index) => {
			const rowEl = document.createElement('div');
			rowEl.className = 'builder-row form-row';

			const labelInput = document.createElement('input');
			labelInput.type = 'text';
			labelInput.placeholder = 'Label text';
			labelInput.value = row.label;
			labelInput.addEventListener('change', () => { row.label = labelInput.value; });

			const bindCell = document.createElement('div');
			const typeSelect = document.createElement('select');
			['parameter', 'field'].forEach((t) => {
				const opt = document.createElement('option');
				opt.value = t;
				opt.textContent = t === 'parameter' ? 'Parameter ($P)' : 'Field ($F)';
				if (t === row.bindType) opt.selected = true;
				typeSelect.appendChild(opt);
			});

			const nameSelect = document.createElement('select');
			const newOpt = document.createElement('option');
			newOpt.value = '';
			newOpt.textContent = '-- new --';
			nameSelect.appendChild(newOpt);

			const nameInput = document.createElement('input');
			nameInput.type = 'text';
			nameInput.placeholder = 'new name';
			nameInput.value = row.isNew ? row.name : '';
			nameInput.classList.toggle('hidden', !row.isNew);

			function populateNames() {
				nameSelect.innerHTML = '';
				nameSelect.appendChild(newOpt.cloneNode(true));
				const source = row.bindType === 'parameter' ? state.design.parameters : state.design.fields;
				source.forEach((item) => {
					const opt = document.createElement('option');
					opt.value = item.name;
					opt.textContent = item.name;
					nameSelect.appendChild(opt);
				});
				nameSelect.value = row.isNew ? '' : row.name;
			}
			populateNames();

			typeSelect.addEventListener('change', () => {
				row.bindType = typeSelect.value;
				row.name = '';
				row.isNew = true;
				populateNames();
				nameInput.classList.remove('hidden');
				nameInput.value = '';
			});
			nameSelect.addEventListener('change', () => {
				row.isNew = nameSelect.value === '';
				row.name = nameSelect.value;
				nameInput.classList.toggle('hidden', !row.isNew);
				nameInput.value = '';
			});
			nameInput.addEventListener('change', () => { row.name = nameInput.value.trim(); });

			bindCell.appendChild(typeSelect);
			bindCell.appendChild(nameSelect);
			bindCell.appendChild(nameInput);

			const removeBtn = document.createElement('button');
			removeBtn.className = 'remove-row';
			removeBtn.textContent = '×';
			removeBtn.addEventListener('click', () => {
				rows.splice(index, 1);
				renderRows(container, state);
			});

			rowEl.appendChild(labelInput);
			rowEl.appendChild(bindCell);
			rowEl.appendChild(removeBtn);
			container.appendChild(rowEl);
		});
	}

	async function insert(state, rerenderAll, bandName, perRow) {
		const band = state.design.bands.find((b) => b.name === bandName);
		if (!band) {
			alert('Could not find the selected band.');
			return;
		}

		const colWidth = Math.floor(state.design.columnWidth / perRow);
		const labelWidth = Math.min(100, colWidth - 60);
		const valueWidth = colWidth - labelWidth - 10;
		let col = 0;
		let y = 0;

		for (const row of rows) {
			if (!row.name) continue;

			if (row.isNew) {
				try {
					if (row.bindType === 'parameter') {
						await Api.addParameter(state.templateType, row.name, 'java.lang.String');
						state.design.parameters.push({ name: row.name, className: 'java.lang.String' });
					} else {
						await Api.addField(state.templateType, row.name, 'java.lang.String');
						state.design.fields.push({ name: row.name, className: 'java.lang.String' });
					}
				} catch (err) {
					alert('Could not create "' + row.name + '": ' + err.message);
					return;
				}
			}

			const x = col * colWidth;
			band.elements.push({
				type: 'staticText', x, y, width: labelWidth, height: 18,
				text: row.label, bold: true, verticalAlign: 'MIDDLE'
			});
			band.elements.push({
				type: 'textField', x: x + labelWidth + 5, y, width: valueWidth, height: 18,
				expression: (row.bindType === 'parameter' ? '$P{' : '$F{') + row.name + '}',
				expressionClass: 'java.lang.String', verticalAlign: 'MIDDLE'
			});

			col++;
			if (col >= perRow) {
				col = 0;
				y += 22;
			}
		}

		band.height = Math.max(band.height, y + (col > 0 ? 22 : 0));

		close();
		rerenderAll();
	}

	return { open, close };
})();
