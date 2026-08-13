const Canvas = (() => {

	function bandOrderKey(name) {
		const leading = { title: 0, pageHeader: 1, columnHeader: 2 };
		if (name in leading) return leading[name];
		if (name.startsWith('groupHeader:')) return 3;
		if (name === 'detail') return 4;
		if (name.startsWith('groupFooter:')) return 5;
		const trailing = { columnFooter: 6, pageFooter: 7, lastPageFooter: 8, summary: 9 };
		return name in trailing ? trailing[name] : 10;
	}

	function sortedBands(design) {
		return [...design.bands].sort((a, b) => bandOrderKey(a.name) - bandOrderKey(b.name));
	}

	function elementLabel(el) {
		if (el.type === 'staticText') return el.text || '(empty text)';
		if (el.type === 'textField') return el.expression || '(empty expression)';
		if (el.type === 'image') return '[image] ' + (el.expression || '');
		return el.type;
	}

	function render(container, state, rerenderAll) {
		container.innerHTML = '';
		const zoom = state.zoom;
		const width = state.design.columnWidth * zoom;

		for (const band of sortedBands(state.design)) {
			const bandEl = document.createElement('div');
			bandEl.className = 'band';
			bandEl.style.width = width + 'px';

			const label = document.createElement('div');
			label.className = 'band-label';
			label.innerHTML = `<span>${band.name}</span><span>height:</span>`;
			const heightInput = document.createElement('input');
			heightInput.type = 'number';
			heightInput.min = '0';
			heightInput.value = band.height;
			heightInput.addEventListener('change', () => {
				band.height = Math.max(0, parseInt(heightInput.value, 10) || 0);
				rerenderAll();
			});
			label.appendChild(heightInput);
			bandEl.appendChild(label);

			const surface = document.createElement('div');
			surface.className = 'band-surface';
			surface.style.width = width + 'px';
			surface.style.height = (band.height * zoom) + 'px';

			surface.addEventListener('dragover', (e) => {
				e.preventDefault();
				surface.classList.add('drop-target');
			});
			surface.addEventListener('dragleave', () => surface.classList.remove('drop-target'));
			surface.addEventListener('drop', (e) => {
				e.preventDefault();
				surface.classList.remove('drop-target');
				Palette.handleDrop(e, band, state, rerenderAll);
			});

			band.elements.forEach((el, index) => {
				surface.appendChild(renderElement(band, el, index, state, rerenderAll));
			});

			bandEl.appendChild(surface);
			container.appendChild(bandEl);
		}
	}

	function renderElement(band, el, index, state, rerenderAll) {
		const zoom = state.zoom;
		const div = document.createElement('div');
		div.className = 'element';
		const isSelected = state.selected && state.selected.bandName === band.name && state.selected.index === index;
		if (isSelected) div.classList.add('selected');
		div.style.left = (el.x * zoom) + 'px';
		div.style.top = (el.y * zoom) + 'px';
		div.style.width = (el.width * zoom) + 'px';
		div.style.height = (el.height * zoom) + 'px';
		div.textContent = elementLabel(el);

		div.addEventListener('mousedown', (e) => {
			if (e.target.classList.contains('handle')) return;
			e.stopPropagation();
			state.selected = { bandName: band.name, index };
			rerenderAll();
			Selection.startMove(e, el, zoom, rerenderAll);
		});

		if (isSelected) {
			['nw', 'ne', 'sw', 'se'].forEach((corner) => {
				const handle = document.createElement('div');
				handle.className = 'handle ' + corner;
				handle.addEventListener('mousedown', (e) => {
					e.stopPropagation();
					Selection.startResize(e, el, corner, zoom, rerenderAll);
				});
				div.appendChild(handle);
			});
		}

		return div;
	}

	return { render, sortedBands };
})();
