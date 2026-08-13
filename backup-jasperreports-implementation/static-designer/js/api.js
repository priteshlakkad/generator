const Api = (() => {

	async function handle(response) {
		if (response.status === 204) {
			return null;
		}
		const contentType = response.headers.get('content-type') || '';
		const body = contentType.includes('application/json') ? await response.json() : await response.text();
		if (!response.ok) {
			const message = (body && body.error) ? body.error : (typeof body === 'string' ? body : 'Request failed');
			throw new Error(message);
		}
		return body;
	}

	function listTemplates() {
		return fetch('/api/templates').then(handle);
	}

	function createBlank(templateType, spec) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/blank`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(spec || {})
		}).then(handle);
	}

	function deleteTemplate(templateType) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}`, { method: 'DELETE' }).then(handle);
	}

	function getDesign(templateType) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/design`).then(handle);
	}

	function saveDesign(templateType, design) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/design`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(design)
		}).then(handle);
	}

	function getJrxmlSource(templateType) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/jrxml`).then(handle);
	}

	function addParameter(templateType, name, className) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/parameters`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ name, className })
		}).then(handle);
	}

	function deleteParameter(templateType, name) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/parameters/${encodeURIComponent(name)}`, {
			method: 'DELETE'
		}).then(handle);
	}

	function addField(templateType, name, className) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/fields`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ name, className })
		}).then(handle);
	}

	function deleteField(templateType, name) {
		return fetch(`/api/templates/${encodeURIComponent(templateType)}/fields/${encodeURIComponent(name)}`, {
			method: 'DELETE'
		}).then(handle);
	}

	return {
		listTemplates, createBlank, deleteTemplate, getDesign, saveDesign, getJrxmlSource,
		addParameter, deleteParameter, addField, deleteField
	};
})();
