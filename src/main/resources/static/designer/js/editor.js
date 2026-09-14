(function () {
	const params = new URLSearchParams(window.location.search);
	const templateType = params.get('type');
	const editing = params.get('mode') === 'edit';
	let quotationData = {};
	const statusEl = document.getElementById('statusMessage');

	if (!templateType) {
		document.body.innerHTML = '<p style="padding:24px">Missing ?type= in URL. <a href="index.html">Back to templates</a></p>';
		return;
	}

	document.getElementById('templateTitle').textContent = templateType;
	document.title = `${templateType} — ${editing ? 'Template Editor' : 'Quotation View'} — QuoteWeave`;
	const modeLink = document.getElementById('modeLink');
	modeLink.textContent = editing ? 'View Quotation' : 'Edit Template';
	modeLink.href = `edit.html?type=${encodeURIComponent(templateType)}${editing ? '' : '&mode=edit'}`;
	document.getElementById('quotationView').classList.toggle('hidden', editing);
	document.getElementById('editorBody').classList.toggle('hidden', !editing);
	document.getElementById('refreshBtn').classList.toggle('hidden', editing);
	['saveBtn', 'previewBtn'].forEach((id) => document.getElementById(id).classList.toggle('hidden', !editing));
	document.getElementById('downloadBtn').textContent = editing ? 'Save & Download PDF' : 'Download PDF';

	function setStatus(message, kind) {
		statusEl.textContent = message;
		statusEl.className = 'status' + (kind ? ' ' + kind : '');
	}

	function escapeHtml(text) {
		const div = document.createElement('div');
		div.textContent = text;
		return div.innerHTML.replace(/"/g, '&quot;');
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

	document.getElementById('columnsBtn').addEventListener('click', async () => {
		try {
			await ColumnManager.open({
				templateType,
				data: editing ? getSampleData() : quotationData,
				onSave: async () => {
					if (!editing) await renderQuotation();
					setStatus('Columns saved', 'success');
				}
			});
		} catch (err) {
			setStatus(err.message, 'error');
		}
	});

	async function save() {
		if (!editing) return;
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
			quotationData = data;
			if (!editing) await renderQuotation();
			document.getElementById('sampleDataOverlay').classList.add('hidden');
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
		const button = document.getElementById('downloadBtn');
		button.disabled = true;
		try {
			const data = editing ? getSampleData() : quotationData;
			await save();
			setStatus('Generating PDF…');
			const blob = await Api.generate(templateType, data);
			const url = URL.createObjectURL(blob);
			const a = document.createElement('a');
			a.href = url;
			a.download = templateType + '.pdf';
			document.body.appendChild(a);
			a.click();
			a.remove();
			setTimeout(() => URL.revokeObjectURL(url), 60000);
			setStatus('PDF downloaded', 'success');
		} catch (err) {
			setStatus(err.message, 'error');
		} finally {
			button.disabled = false;
		}
	});

	async function renderQuotation() {
		const message = document.getElementById('viewMessage');
		const frame = document.getElementById('quotationFrame');
		message.textContent = 'Loading quotation…';
		message.className = 'view-message';
		frame.classList.add('hidden');
		try {
			frame.srcdoc = await Api.preview(templateType, quotationData);
			frame.classList.remove('hidden');
			message.classList.add('hidden');
		} catch (err) {
			message.textContent = `Could not load quotation: ${err.message}. Use Refresh to try again.`;
			message.className = 'view-message error';
			throw err;
		}
	}

	document.getElementById('refreshBtn').addEventListener('click', async () => {
		try {
			await load();
		} catch (err) {
			showLoadError(err);
		}
	});

	async function load() {
		setStatus('Loading…');
		['columnsBtn', 'sampleDataBtn', 'refreshBtn', 'previewBtn', 'downloadBtn', 'saveBtn']
			.forEach((id) => { document.getElementById(id).disabled = true; });
		const sampleData = await Api.getSampleData(templateType);
		quotationData = sampleData;
		document.getElementById('sampleDataJson').value = JSON.stringify(sampleData, null, 2);

		if (editing) {
			const html = await Api.getHtml(templateType);
			if (isFullDocument(html)) {
				loadSourceEditor(html);
			} else {
				await loadRichTextEditor(html);
			}
		} else {
			await renderQuotation();
		}
		['columnsBtn', 'sampleDataBtn', 'refreshBtn', 'previewBtn', 'downloadBtn', 'saveBtn']
			.forEach((id) => { document.getElementById(id).disabled = false; });
		setStatus('');
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
		return tinymce.init({
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

	function showLoadError(err) {
		setStatus(err.message, 'error');
		if (!editing) {
			document.getElementById('quotationFrame').classList.add('hidden');
			const message = document.getElementById('viewMessage');
			message.textContent = `Could not load quotation: ${err.message}. Use Refresh to try again.`;
			message.className = 'view-message error';
			document.getElementById('refreshBtn').disabled = false;
		}
	}

	load().catch(showLoadError);
})();
