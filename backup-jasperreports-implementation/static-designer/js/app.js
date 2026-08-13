(async function () {
	const params = new URLSearchParams(window.location.search);
	const templateType = params.get('type');
	const statusEl = document.getElementById('statusMessage');

	if (!templateType) {
		document.body.innerHTML = '<p style="padding:24px">Missing ?type= in URL. <a href="index.html">Back to templates</a></p>';
		return;
	}

	document.getElementById('templateTitle').textContent = templateType;

	const state = { templateType, design: null, selected: null, zoom: 1 };

	const canvasContainer = document.getElementById('canvas');
	const inspectorContainer = document.getElementById('inspector');
	const readOnlyPanel = document.getElementById('readOnlyPanel');
	const dataPanel = document.getElementById('dataPanel');

	function setStatus(message, kind) {
		statusEl.textContent = message;
		statusEl.className = 'status' + (kind ? ' ' + kind : '');
	}

	function renderReadOnlyPanel() {
		readOnlyPanel.innerHTML = '';
		if (!state.design.readOnlyBands || state.design.readOnlyBands.length === 0) return;
		const details = document.createElement('details');
		const summary = document.createElement('summary');
		summary.textContent = `${state.design.readOnlyBands.length} section(s) not editable in this version`;
		details.appendChild(summary);
		const list = document.createElement('ul');
		state.design.readOnlyBands.forEach((r) => {
			const li = document.createElement('li');
			li.textContent = `${r.name} — ${r.reason}`;
			list.appendChild(li);
		});
		details.appendChild(list);
		readOnlyPanel.appendChild(details);
	}

	function renderAll() {
		Canvas.render(canvasContainer, state, renderAll);
		Inspector.render(inspectorContainer, state, renderAll);
		Inspector.renderDataPanel(dataPanel, state, renderAll);
	}

	async function load() {
		state.design = await Api.getDesign(templateType);
		document.getElementById('templateMeta').textContent =
			`${state.design.orientation} · ${state.design.pageWidth}×${state.design.pageHeight}`;
		state.selected = null;
		renderReadOnlyPanel();
		renderAll();
	}

	Palette.init(document.getElementById('palette'));

	document.getElementById('saveBtn').addEventListener('click', async () => {
		setStatus('Saving…');
		try {
			state.design = await Api.saveDesign(templateType, state.design);
			state.selected = null;
			renderReadOnlyPanel();
			renderAll();
			setStatus('Saved', 'success');
		} catch (err) {
			setStatus(err.message, 'error');
		}
	});

	document.getElementById('viewSourceBtn').addEventListener('click', async () => {
		const source = await Api.getJrxmlSource(templateType);
		document.getElementById('sourceContent').textContent = source;
		document.getElementById('sourceOverlay').classList.remove('hidden');
	});

	document.getElementById('closeSourceBtn').addEventListener('click', () => {
		document.getElementById('sourceOverlay').classList.add('hidden');
	});

	document.getElementById('insertTableBtn').addEventListener('click', () => {
		TableBuilder.open(state, renderAll);
	});
	document.getElementById('closeTableBuilderBtn').addEventListener('click', () => {
		TableBuilder.close();
	});

	document.getElementById('insertFormBtn').addEventListener('click', () => {
		FormFieldBuilder.open(state, renderAll);
	});
	document.getElementById('closeFormFieldBtn').addEventListener('click', () => {
		FormFieldBuilder.close();
	});

	try {
		await load();
	} catch (err) {
		setStatus(err.message, 'error');
	}
})();
