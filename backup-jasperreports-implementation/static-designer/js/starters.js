const Starters = (() => {

	const SIMPLE_INVOICE = {
		parameters: [
			{ name: 'companyName', className: 'java.lang.String' },
			{ name: 'documentNumber', className: 'java.lang.String' },
			{ name: 'documentDate', className: 'java.lang.String' }
		],
		fields: [
			{ name: 'itemName', className: 'java.lang.String' },
			{ name: 'qty', className: 'java.lang.String' },
			{ name: 'amount', className: 'java.lang.String' }
		],
		applyTo(design) {
			const title = design.bands.find((b) => b.name === 'title');
			title.height = 90;
			title.elements = [
				{ type: 'staticText', x: 0, y: 0, width: design.columnWidth, height: 28,
					text: 'INVOICE', bold: true, fontSize: 20, horizontalAlign: 'CENTER', verticalAlign: 'MIDDLE' },
				{ type: 'textField', x: 0, y: 32, width: design.columnWidth, height: 18,
					expression: '$P{companyName}', expressionClass: 'java.lang.String',
					horizontalAlign: 'CENTER', verticalAlign: 'MIDDLE' },
				{ type: 'staticText', x: 0, y: 60, width: 90, height: 18, text: 'Document No.', bold: true, verticalAlign: 'MIDDLE' },
				{ type: 'textField', x: 95, y: 60, width: 150, height: 18,
					expression: '$P{documentNumber}', expressionClass: 'java.lang.String', verticalAlign: 'MIDDLE' },
				{ type: 'staticText', x: 260, y: 60, width: 60, height: 18, text: 'Date', bold: true, verticalAlign: 'MIDDLE' },
				{ type: 'textField', x: 325, y: 60, width: 150, height: 18,
					expression: '$P{documentDate}', expressionClass: 'java.lang.String', verticalAlign: 'MIDDLE' }
			];

			const columnWidth = Math.floor(design.columnWidth / 3);
			const columnHeader = design.bands.find((b) => b.name === 'columnHeader');
			columnHeader.height = 22;
			columnHeader.elements = ['Item', 'Qty', 'Amount'].map((label, i) => ({
				type: 'staticText', x: i * columnWidth, y: 0, width: columnWidth, height: 22,
				text: label, bold: true, mode: 'OPAQUE', backColor: '#D9D9D9',
				horizontalAlign: 'CENTER', verticalAlign: 'MIDDLE', border: true
			}));

			const detail = design.bands.find((b) => b.name === 'detail');
			detail.height = 20;
			detail.elements = ['itemName', 'qty', 'amount'].map((fieldName, i) => ({
				type: 'textField', x: i * columnWidth, y: 0, width: columnWidth, height: 20,
				expression: '$F{' + fieldName + '}', expressionClass: 'java.lang.String',
				horizontalAlign: i === 0 ? 'LEFT' : 'RIGHT', verticalAlign: 'MIDDLE', border: true
			}));
		}
	};

	const STARTERS = {
		blank: { label: 'Blank', parameters: [], fields: [], applyTo: () => {} },
		'simple-invoice': { label: 'Simple Invoice', ...SIMPLE_INVOICE }
	};

	async function apply(templateType, starterKey) {
		if (!starterKey || starterKey === 'blank') {
			return;
		}
		const starter = STARTERS[starterKey];
		if (!starter) {
			return;
		}
		for (const p of starter.parameters) {
			await Api.addParameter(templateType, p.name, p.className);
		}
		for (const f of starter.fields) {
			await Api.addField(templateType, f.name, f.className);
		}
		const design = await Api.getDesign(templateType);
		starter.applyTo(design);
		await Api.saveDesign(templateType, design);
	}

	return { STARTERS, apply };
})();
