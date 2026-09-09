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

	// Whichever editor ends up loading sets this; see load().
	let getEditorHtml = () => '';

	/**
	 * A template that carries its own <head>/<style>/doctype is a full print document. TinyMCE only
	 * models body content, so round-tripping one through it silently drops the page rules, colgroup
	 * widths and print CSS the PDF depends on. Those are edited as raw HTML instead.
	 */
	function isFullDocument(html) {
		return /<!DOCTYPE|<html[\s>]|<head[\s>]|<style[\s>]/i.test(html);
	}

	// ---- column toggles -------------------------------------------------

	const ALIGNMENTS = ['', 'left', 'center', 'right'];

	function renderColumns(config) {
		const container = document.getElementById('columnsList');
		container.textContent = '';

		const groups = (config && config.groups) || [];
		if (groups.length === 0) {
			container.innerHTML = '<p class="empty-state">This template has no table marked with ' +
				'<code>data-columns</code>, so there are no columns to configure.</p>';
			return;
		}

		groups.forEach((group) => {
			const groupEl = document.createElement('div');
			groupEl.className = 'column-group';
			groupEl.dataset.groupId = group.id;
			groupEl.innerHTML =
				`<div class="column-group-name">${escapeHtml(group.id)}</div>` +
				'<div class="column-header"><span class="h-toggle"></span><span class="h-field">Field</span>' +
				'<span class="h-label">Header label</span><span class="h-width">Width %</span>' +
				'<span class="h-align">Align</span></div>';

			(group.columns || []).forEach((column) => {
				groupEl.appendChild(renderColumnRow(column));
			});
			container.appendChild(groupEl);
		});
	}

	function renderColumnRow(column) {
		const row = document.createElement('div');
		row.className = 'column-row';
		row.dataset.field = column.field;

		const visible = column.visible !== false;
		const options = ALIGNMENTS
			.map((value) => `<option value="${value}"${value === (column.align || '') ? ' selected' : ''}>` +
				`${value || 'default'}</option>`)
			.join('');

		row.innerHTML =
			`<input type="checkbox" class="column-visible"${visible ? ' checked' : ''}/>` +
			`<span class="column-field" title="${escapeHtml(column.field)}">${escapeHtml(column.field)}</span>` +
			`<input type="text" class="column-label" value="${escapeHtml(column.label || '')}"/>` +
			`<input type="number" class="column-width" step="0.5" min="0" value="${column.width == null ? '' : column.width}"/>` +
			`<select class="column-align">${options}</select>`;

		const checkbox = row.querySelector('.column-visible');
		const syncDimming = () => row.classList.toggle('is-hidden', !checkbox.checked);
		checkbox.addEventListener('change', syncDimming);
		syncDimming();
		return row;
	}

	function collectColumns() {
		const groups = Array.from(document.querySelectorAll('#columnsList .column-group')).map((groupEl) => ({
			id: groupEl.dataset.groupId,
			columns: Array.from(groupEl.querySelectorAll('.column-row')).map((row) => {
				const width = row.querySelector('.column-width').value.trim();
				const align = row.querySelector('.column-align').value;
				return {
					field: row.dataset.field,
					label: row.querySelector('.column-label').value,
					width: width === '' ? null : Number(width),
					align: align === '' ? null : align,
					visible: row.querySelector('.column-visible').checked
				};
			})
		}));
		return { groups };
	}

	document.getElementById('columnsBtn').addEventListener('click', async () => {
		try {
			// Deliberately reads the stored template rather than saving the editor first:
			// opening this panel must never overwrite a template as a side effect.
			renderColumns(await Api.getColumns(templateType));
			document.getElementById('columnsOverlay').classList.remove('hidden');
		} catch (err) {
			setStatus(err.message, 'error');
		}
	});
	document.getElementById('closeColumnsBtn').addEventListener('click', () => {
		document.getElementById('columnsOverlay').classList.add('hidden');
	});
	document.getElementById('saveColumnsBtn').addEventListener('click', async () => {
		setStatus('Saving columns…');
		try {
			await Api.saveColumns(templateType, collectColumns());
			setStatus('Columns saved', 'success');
		} catch (err) {
			setStatus(err.message, 'error');
		}
	});

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

		if (isFullDocument(html)) {
			loadSourceEditor(html);
		} else {
			loadRichTextEditor(html);
		}
	}

	function loadSourceEditor(html) {
		const textarea = document.getElementById('editor');
		textarea.value = html;
		textarea.spellcheck = false;
		textarea.classList.add('source-editor');
		document.getElementById('sourceModeNotice').classList.remove('hidden');
		getEditorHtml = () => textarea.value;
	}

	function loadRichTextEditor(html) {
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
			getEditorHtml = () => editors[0].getContent();
		});
	}

	load().catch((err) => setStatus(err.message, 'error'));
})();
