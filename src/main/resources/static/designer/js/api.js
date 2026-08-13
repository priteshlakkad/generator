const Api = (() => {

	async function handle(response) {
		if (response.status === 204) {
			return null;
		}
		const contentType = response.headers.get('content-type') || '';
		const isJson = contentType.includes('application/json');
		const body = isJson ? await response.json() : await response.text();
		if (!response.ok) {
			const message = (body && body.error) ? body.error : (typeof body === 'string' ? body : 'Request failed');
			throw new Error(message);
		}
		return body;
	}

	function listTemplates() {
		return fetch('/api/templates').then(handle);
	}

	function createBlank(templateType) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/blank`, { method: 'POST' }).then(handle);
	}

	function deleteTemplate(templateType) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}`, { method: 'DELETE' }).then(handle);
	}

	function copyTemplate(templateType, newTemplateType) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/copy?newTemplateType=${encodeURIComponent(newTemplateType)}`, {
			method: 'POST'
		}).then(handle);
	}

	function getSampleData(templateType) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/sample`).then(handle);
	}

	function saveSampleData(templateType, json) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/sample`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json' },
			body: json
		}).then(handle);
	}

	function getHtml(templateType) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/html`).then(handle);
	}

	function saveHtml(templateType, html) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/html`, {
			method: 'PUT',
			headers: { 'Content-Type': 'text/plain' },
			body: html
		}).then(handle);
	}

	function generate(templateType, data) {
		return fetch(`/api/pdf/generate/${encodeURIComponent(templateType)}`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(data || {})
		}).then(async (response) => {
			if (!response.ok) {
				const body = await response.json().catch(() => ({}));
				throw new Error(body.error || 'PDF generation failed');
			}
			return response.blob();
		});
	}

	function preview(templateType, data) {
		return fetch(`/api/pdf/preview/${encodeURIComponent(templateType)}`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(data || {})
		}).then(handle);
	}

	return {
		listTemplates, createBlank, deleteTemplate, copyTemplate,
		getHtml, saveHtml, getSampleData, saveSampleData,
		generate, preview
	};
})();
