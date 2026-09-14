const ColumnManager = (() => {
    const overlay = document.getElementById('columnsOverlay');
    const list = document.getElementById('columnsList');
    const status = document.getElementById('columnsStatus');
    const warnings = document.getElementById('columnsWarnings');
    const frame = document.getElementById('columnsPreviewFrame');
    const saveButton = document.getElementById('saveColumnsBtn');
    const closeButton = document.getElementById('closeColumnsBtn');
    let session = null;
    let returnFocus = null;

    const escape = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
    const copy = value => JSON.parse(JSON.stringify(value));

    function message(text, error = false) {
        status.textContent = text;
        status.className = error ? 'error' : '';
    }

    function cancel() {
        if (!session || session.saving) return;
        clearTimeout(session.timer);
        session.controller?.abort();
        session = null;
        overlay.classList.add('hidden');
        frame.removeAttribute('srcdoc');
        returnFocus?.focus();
    }
    closeButton.addEventListener('click', cancel);
    overlay.addEventListener('keydown', event => {
        if (event.key === 'Escape') cancel();
        if (event.key === 'Tab') {
            const focusable = [...overlay.querySelectorAll('button, input, select, iframe')].filter(el => !el.disabled && el.offsetParent !== null);
            const first = focusable[0], last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
        }
    });

    async function open(options) {
        if (session?.saving) return;
        if (session) cancel();
        returnFocus = document.activeElement;
        const current = { ...options, data: copy(options.data), config: {groups: []}, metadata: {}, revision: 0, validRevision: -1, saving: false };
        session = current;
        list.textContent = '';
        warnings.textContent = '';
        frame.removeAttribute('srcdoc');
        saveButton.disabled = true;
        overlay.classList.remove('hidden');
        closeButton.focus();
        message('Loading columns…');
        try {
            current.config = await Api.getColumns(current.templateType);
            if (session !== current) return;
            render();
            await preview(current);
        } catch (err) {
            if (session === current) message(err.message, true);
        }
    }

    function changed() {
        if (!session) return;
        session.revision++;
        session.validRevision = -1;
        saveButton.disabled = true;
        message('Updating draft…');
        frame.classList.add('is-updating');
        clearTimeout(session.timer);
        session.controller?.abort();
        const current = session;
        current.timer = setTimeout(() => preview(current), 250);
    }

    async function preview(current) {
        if (session !== current) return;
        const revision = current.revision;
        current.controller = new AbortController();
        try {
            const result = await Api.previewColumns(current.templateType, current.config, current.data, current.controller.signal);
            if (session !== current || revision !== current.revision) return;
            const firstMetadata = !current.hasMetadata;
            current.metadata = result.metadata;
            current.hasMetadata = true;
            if (firstMetadata) render();
            frame.srcdoc = result.html;
            frame.classList.remove('is-updating');
            warnings.textContent = (result.warnings || []).join(' ');
            for (const group of result.config.groups) {
                for (const column of group.columns) {
                    const row = [...list.querySelectorAll('.managed-column')].find(el => el.dataset.group === group.id && el.dataset.field === column.field);
                    if (row) row.querySelector('.resolved-width').textContent = column.visible === false ? 'Hidden by sample data' : `${column.resolvedWidth ?? 0}% actual`;
                }
            }
            current.validRevision = revision;
            saveButton.disabled = current.config.groups.length === 0;
            message('Draft ready — changes have not been saved.');
        } catch (err) {
            if (session !== current || revision !== current.revision || err.name === 'AbortError') return;
            current.validRevision = -1;
            saveButton.disabled = true;
            frame.removeAttribute('srcdoc');
            frame.classList.remove('is-updating');
            message(err.message, true);
        }
    }

    function render() {
        list.textContent = '';
        for (const [groupIndex, group] of session.config.groups.entries()) {
            const section = document.createElement('section');
            section.className = 'managed-group';
            const metadata = session.metadata[group.id] || {};
            section.innerHTML = `<div class="managed-group-heading"><strong>${escape(group.id)}</strong><button data-auto>Auto size all</button></div>`;
            section.querySelector('[data-auto]').addEventListener('click', () => {
                group.columns.forEach(column => { column.sizing = 'auto'; });
                render(); changed();
            });
            const visible = group.columns.filter(c => c.visible !== false);
            for (const [index, column] of visible.entries()) section.appendChild(columnRow(group, column, index, visible));
            const removed = group.columns.filter(c => c.visible === false);
            if (removed.length) {
                const restore = document.createElement('div');
                restore.className = 'removed-columns';
                restore.innerHTML = '<strong>Removed columns</strong>';
                for (const column of removed) {
                    const button = document.createElement('button');
                    button.textContent = `Restore ${column.label || column.field}`;
                    button.addEventListener('click', () => { column.visible = true; render(); changed(); });
                    restore.appendChild(button);
                }
                section.appendChild(restore);
            }
            if (metadata.canAdd) section.appendChild(addForm(group, groupIndex, metadata.fields || {}));
            else {
                const note = document.createElement('p');
                note.className = 'template-meta';
                note.textContent = session.hasMetadata ? 'Adding columns requires a simple repeating table with marked headers and cells.' : 'Loading available data fields…';
                section.appendChild(note);
            }
            list.appendChild(section);
        }
        if (!session.config.groups.length) list.innerHTML = '<p class="empty-state">This template has no configurable table columns.</p>';
    }

    function columnRow(group, column, index, visible) {
        const row = document.createElement('div');
        row.className = 'managed-column';
        row.dataset.group = group.id;
        row.dataset.field = column.field;
        const mode = column.sizing || 'proportional';
        row.innerHTML = `<div class="managed-column-heading"><span>${escape(column.field)}${column.custom ? ' · Added' : ''}</span>
            <div><button data-up aria-label="Move ${escape(column.field)} up" ${index === 0 ? 'disabled' : ''}>↑</button>
            <button data-down aria-label="Move ${escape(column.field)} down" ${index === visible.length - 1 ? 'disabled' : ''}>↓</button>
            <button data-remove ${visible.length === 1 ? 'disabled' : ''}>Remove</button></div></div>
            <div class="managed-column-fields">
            <label>Heading<input data-label value="${escape(column.label || column.field)}"></label>
            <label>Width mode<select data-sizing>${['proportional', 'auto', 'fixed'].map(m => `<option value="${m}" ${mode === m ? 'selected' : ''}>${m === 'proportional' ? 'Proportional' : m === 'auto' ? 'Auto' : 'Fixed %'}</option>`).join('')}</select></label>
            <label>Width %<input data-width type="number" min="0.01" max="100" step="0.01" value="${escape(column.width)}" ${mode === 'auto' ? 'disabled' : ''}></label>
            <label>Align<select data-align>${['', 'left', 'center', 'right'].map(a => `<option value="${a}" ${(column.align || '') === a ? 'selected' : ''}>${a || 'Default'}</option>`).join('')}</select></label>
            </div><small class="resolved-width">Calculating…</small>`;
        row.querySelector('[data-label]').addEventListener('input', e => { column.label = e.target.value; changed(); });
        row.querySelector('[data-width]').addEventListener('input', e => { column.width = e.target.value === '' ? null : Number(e.target.value); changed(); });
        row.querySelector('[data-align]').addEventListener('change', e => { column.align = e.target.value; changed(); });
        row.querySelector('[data-sizing]').addEventListener('change', e => {
            column.sizing = e.target.value;
            row.querySelector('[data-width]').disabled = column.sizing === 'auto';
            changed();
        });
        row.querySelector('[data-remove]').addEventListener('click', () => { column.visible = false; render(); changed(); });
        const move = other => {
            group.ordered = true;
            const from = group.columns.indexOf(column), to = group.columns.indexOf(other);
            [group.columns[from], group.columns[to]] = [group.columns[to], group.columns[from]];
            render(); changed();
        };
        row.querySelector('[data-up]').addEventListener('click', () => move(visible[index - 1]));
        row.querySelector('[data-down]').addEventListener('click', () => move(visible[index + 1]));
        return row;
    }

    function addForm(group, groupIndex, fields) {
        const form = document.createElement('form');
        form.className = 'add-column-form';
        form.innerHTML = `<strong>Add Column</strong><div class="add-column-fields">
            <label>Data field<input data-field list="column-fields-${groupIndex}" required pattern="[a-zA-Z_][a-zA-Z0-9_]*" placeholder="Choose or enter a field"></label>
            <datalist id="column-fields-${groupIndex}">${Object.keys(fields).filter(field => !group.columns.some(c => c.field === field)).map(field => `<option value="${escape(field)}"></option>`).join('')}</datalist>
            <label>Heading<input data-heading placeholder="e.g. Brand"></label>
            <label>Value type<select data-type><option value="text">Text</option><option value="number">Number</option></select></label>
            <button class="primary" type="submit">Add</button></div><p class="template-meta">Choose a sample data field or enter a new field name. Values must be supplied in the item data.</p>`;
        form.querySelector('[data-field]').addEventListener('input', event => {
            form.querySelector('[data-type]').value = fields[event.target.value] || 'text';
        });
        form.addEventListener('submit', event => {
            event.preventDefault();
            const field = form.querySelector('[data-field]').value.trim();
            if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(field)) { message('Enter a valid data field name.', true); return; }
            if (group.columns.some(column => column.field === field)) { message('That field already exists. Use Restore if it was removed.', true); return; }
            const type = form.querySelector('[data-type]').value;
            group.columns.push({field, label: form.querySelector('[data-heading]').value.trim() || field, type,
                align: type === 'number' ? 'right' : 'left', visible: true, custom: true, sizing: 'auto', width: null});
            render(); changed();
        });
        return form;
    }

    saveButton.addEventListener('click', async () => {
        const current = session;
        if (!current || current.saving || current.validRevision !== current.revision) return;
        current.saving = true;
        saveButton.disabled = true;
        closeButton.disabled = true;
        list.inert = true;
        message('Saving columns…');
        let saved = false;
        try {
            // Resolved widths are output, never persisted as user preferences.
            const config = copy(current.config);
            config.groups.forEach(group => group.columns.forEach(column => { delete column.resolvedWidth; }));
            await Api.saveColumns(current.templateType, config);
            saved = true;
            await current.onSave();
            current.saving = false;
            cancel();
        } catch (err) {
            message(`${saved ? 'Columns were saved, but the quotation could not refresh: ' : ''}${err.message}`, true);
            saveButton.disabled = saved;
        } finally {
            current.saving = false;
            closeButton.disabled = false;
            list.inert = false;
        }
    });
    return { open };
})();
