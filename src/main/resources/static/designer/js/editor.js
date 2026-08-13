(function () {
	const params = new URLSearchParams(window.location.search);
	const templateType = params.get('type');
	const statusEl = document.getElementById('statusMessage');

	if (!templateType) {
		document.body.innerHTML = '<p style="padding:24px">Missing ?type= in URL. <a href="index.html">Back to templates</a></p>';
		return;
	}

	document.getElementById('templateTitle').textContent = templateType;

	function setStatus(message, kind) {
		statusEl.textContent = message;
		statusEl.className = 'status' + (kind ? ' ' + kind : '');
	}

	function escapeHtml(text) {
		const div = document.createElement('div');
		div.textContent = text;
		return div.innerHTML;
	}

	function getSampleData() {
		const raw = document.getElementById('sampleDataJson').value;
		try {
			return JSON.parse(raw || '{}');
		} catch (err) {
			throw new Error('Sample data is not valid JSON: ' + err.message);
		}
	}

	function buildTableHtml(arrayKey, columns) {
		const headerCells = columns.map((c) => `<th>${escapeHtml(c.label)}</th>`).join('');
		const dataCells = columns.map((c) => `<td>{{${escapeHtml(c.field)}}}</td>`).join('');
		return `<table style="border-collapse:collapse;width:100%" border="1">
			<thead><tr>${headerCells}</tr></thead>
			<tbody><tr data-repeat="${escapeHtml(arrayKey)}">${dataCells}</tr></tbody>
			</table><p></p>`;
	}

	function openInsertFieldDialog(editor) {
		editor.windowManager.open({
			title: 'Insert Field',
			body: {
				type: 'panel',
				items: [{ type: 'input', name: 'fieldName', label: 'Field name (e.g. customerName)' }]
			},
			buttons: [
				{ type: 'cancel', text: 'Cancel' },
				{ type: 'submit', text: 'Insert', primary: true }
			],
			onSubmit: (api) => {
				const name = (api.getData().fieldName || '').trim();
				if (name) {
					editor.insertContent('{{' + name + '}}');
				}
				api.close();
			}
		});
	}

	function openInsertTableDialog(editor) {
		editor.windowManager.open({
			title: 'Insert Data Table',
			body: {
				type: 'panel',
				items: [
					{ type: 'input', name: 'arrayKey', label: 'Data array name (e.g. items)' },
					{
						type: 'textarea', name: 'columns',
						label: 'Columns, one per line, as field:Label'
					}
				]
			},
			initialData: {
				arrayKey: 'items',
				columns: 'name:Name\nqty:Qty\namount:Amount'
			},
			buttons: [
				{ type: 'cancel', text: 'Cancel' },
				{ type: 'submit', text: 'Insert', primary: true }
			],
			onSubmit: (api) => {
				const data = api.getData();
				const arrayKey = (data.arrayKey || 'items').trim();
				const columns = data.columns.split('\n')
					.map((line) => line.trim())
					.filter(Boolean)
					.map((line) => {
						const separatorIndex = line.indexOf(':');
						const field = (separatorIndex === -1 ? line : line.slice(0, separatorIndex)).trim();
						const label = separatorIndex === -1 ? field : line.slice(separatorIndex + 1).trim();
						return { field, label: label || field };
					});
				if (columns.length > 0) {
					editor.insertContent(buildTableHtml(arrayKey, columns));
				}
				api.close();
			}
		});
	}

	let tinymceEditor = null;

	function getEditorHtml() {
		return tinymceEditor.getContent();
	}

	async function save() {
		await Api.saveHtml(templateType, getEditorHtml());
	}

	document.getElementById('saveBtn').addEventListener('click', async () => {
		setStatus('Saving…');
		try {
			await save();
			setStatus('Saved', 'success');
		} catch (err) {
			setStatus(err.message, 'error');
		}
	});

	document.getElementById('sampleDataBtn').addEventListener('click', () => {
		document.getElementById('sampleDataOverlay').classList.remove('hidden');
	});
	document.getElementById('closeSampleDataBtn').addEventListener('click', () => {
		document.getElementById('sampleDataOverlay').classList.add('hidden');
	});
	document.getElementById('saveSampleDataBtn').addEventListener('click', async () => {
		try {
			const data = getSampleData();
			await Api.saveSampleData(templateType, JSON.stringify(data));
			setStatus('Sample data saved', 'success');
		} catch (err) {
			setStatus(err.message, 'error');
		}
	});

	document.getElementById('previewBtn').addEventListener('click', async () => {
		setStatus('Saving…');
		try {
			const data = getSampleData();
			await save();
			setStatus('Rendering preview…');
			const html = await Api.preview(templateType, data);
			const frame = document.getElementById('previewFrame');
			frame.srcdoc = html;
			document.getElementById('previewOverlay').classList.remove('hidden');
			setStatus('Saved', 'success');
		} catch (err) {
			setStatus(err.message, 'error');
		}
	});
	document.getElementById('closePreviewBtn').addEventListener('click', () => {
		document.getElementById('previewOverlay').classList.add('hidden');
	});

	document.getElementById('downloadBtn').addEventListener('click', async () => {
		setStatus('Saving…');
		try {
			const data = getSampleData();
			await save();
			setStatus('Generating PDF…');
			const blob = await Api.generate(templateType, data);
			const url = URL.createObjectURL(blob);
			const a = document.createElement('a');
			a.href = url;
			a.download = templateType + '.pdf';
			a.click();
			URL.revokeObjectURL(url);
			setStatus('Saved', 'success');
		} catch (err) {
			setStatus(err.message, 'error');
		}
	});

	async function load() {
		const sampleData = await Api.getSampleData(templateType);
		document.getElementById('sampleDataJson').value = JSON.stringify(sampleData, null, 2);

		const html = await Api.getHtml(templateType);

		tinymce.init({
			selector: '#editor',
			license_key: 'gpl',
			base_url: 'vendor/tinymce',
			suffix: '.min',
			height: '100%',
			resize: false,
			menubar: false,
			plugins: 'table image lists link',
			toolbar: 'undo redo | blocks | bold italic underline | alignleft aligncenter alignright | ' +
				'bullist numlist | table image link | insertfield inserttable',
			setup: (editor) => {
				editor.ui.registry.addButton('insertfield', {
					text: 'Field',
					tooltip: 'Insert a merge field',
					onAction: () => openInsertFieldDialog(editor)
				});
				editor.ui.registry.addButton('inserttable', {
					text: 'Data Table',
					tooltip: 'Insert a repeating data table',
					onAction: () => openInsertTableDialog(editor)
				});
				editor.on('init', () => {
					editor.setContent(html);
				});
			}
		}).then((editors) => {
			tinymceEditor = editors[0];
		});
	}

	load().catch((err) => setStatus(err.message, 'error'));
})();
